package com.agentsquad.squad.service;

import com.agentsquad.squad.enums.ActivityType;
import com.agentsquad.squad.enums.PlanJoinPolicy;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.mapper.MessageAcceptanceMapper;
import com.agentsquad.squad.model.bo.JobReusedPayloadBO;
import com.agentsquad.squad.model.bo.PlanAcceptanceCriterionBO;
import com.agentsquad.squad.model.bo.PlanPublishedPayloadBO;
import com.agentsquad.squad.model.bo.ActivityBusinessFactBO;
import com.agentsquad.squad.mapper.ConversationReadMapper;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * 目标公开事实到统一会话消息的事务投影。
 * <p>
 * 保留原任务事件用于诊断，同时写会话事件与交付消息；与原生日志水位一同提交，重放不重复生成正文。
 */
@Service
public class TaskEventPublishingService {
    @Resource
    private ActivityProjectionService activities;
    @Resource
    private FinalDeliveryService finalDeliveries;

    @Resource
    private TaskEventMapper taskEvents;

    @Resource
    private TaskMapper tasks;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ConversationEventWriterService events;

    @Resource
    private MessageAcceptanceMapper sequences;

    @Resource
    private ConversationReadMapper reads;

    @Resource
    private ObjectMapper json;

    /**
     * 调用方已按会话和实际所有者加锁；完成答案只来自已裁决的公开事件，内部回合不成为新用户消息。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int insertProjected(String userId, String taskId, long sequence, String producerKey,
                               String eventType, String payloadJson, LocalDateTime occurredAt) {
        int inserted = taskEvents.insertProjected(userId, taskId, sequence, producerKey, eventType, payloadJson, occurredAt);
        var scope = scopes.lockOwned(userId, scopes.scopeForTask(userId, taskId), false);
        var header = reads.header(userId, scope.getConversationId());
        if (header == null || header.getGeneration() != scope.getConversationGeneration()) {
            return inserted;
        }
        String key = "task-event:" + taskId + ":" + sequence;
        var task = tasks.lockOwnedRow(userId, taskId);
        if ("answer.completed".equals(eventType) || "answer.replaced".equals(eventType)) {
            try {
                var payload = RuntimePayloads.readJson(json, payloadJson);
                String text = payload.path("text").asText();
                long revision = payload.path("revision").asLong(task.getAnswerRevision());
                String sourceMessageId = reads.taskSource(userId, taskId);
                String claimedId = sourceMessageId == null ? null
                        : finalDeliveries.claimPublication(userId, sourceMessageId, "TASK", taskId);
                if (sourceMessageId != null && claimedId == null) {
                    return inserted;
                }
                String messageId = reads.taskAnswer(userId, taskId);
                if (messageId == null) {
                    messageId = claimedId == null ? UUID.randomUUID().toString() : claimedId;
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    sequences.reserveMessage(userId, scope.getConversationId(), now);
                    reads.insertTaskAnswer(messageId, userId, scope.getConversationId(), sequences.messageSequence(userId, scope.getConversationId()), text, revision, reads.taskSource(userId, taskId), taskId, now);
                } else {
                    // 完成事件重放不能把已修订正文恢复成旧答案；修订也只能推进消息版本。
                    if ("answer.completed".equals(eventType)
                            || reads.replaceTaskAnswer(userId, messageId, text, revision) != 1) {
                        return inserted;
                    }
                }
                events.publish(scope, key, "message.completed", Map.of("messageId", messageId, "revision", revision, "taskId", taskId, "text", text));
                activities.completed(scope, new ActivityBusinessFactBO(ActivityType.ANSWER_PUBLISH,
                        messageId + ":" + revision));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("目标公开答案无法读取", exception);
            }
        } else if ("plan.published".equals(eventType) || "job.reused".equals(eventType)) {
            try {
                var payload = json.readTree(payloadJson);
                long planVersion = payload.path("planVersion").asLong();
                if (planVersion != task.getPlanVersion()) {
                    return inserted;
                }
                if ("plan.published".equals(eventType)) {
                    java.util.List<String> jobIds = json.convertValue(payload.path("jobIds"),
                            json.getTypeFactory().constructCollectionType(java.util.List.class, String.class));
                    java.util.List<PlanAcceptanceCriterionBO> criteria = json.convertValue(
                            payload.path("acceptanceCriteria"), json.getTypeFactory().constructCollectionType(java.util.List.class,
                                    PlanAcceptanceCriterionBO.class));
                    events.publish(scope, key, eventType, new PlanPublishedPayloadBO(2,
                            taskId, planVersion, jobIds, PlanJoinPolicy.valueOf(
                            payload.path("joinPolicy").asText()), criteria));
                } else {
                    events.publish(scope, key, eventType, new JobReusedPayloadBO(2,
                            taskId, planVersion, payload.path("jobId").asText(), payload.path("reusedFromJobId").asText()));
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("计划公开载荷无法解析", exception);
            }
        } else if (eventType.startsWith("interaction.")) {
            events.publish(scope, key, eventType, Map.of("taskId", taskId));
        } else {
            if ("task.completed".equals(eventType)
                    && ("CANCELLED".equals(task.getStatus()) || "FAILED".equals(task.getStatus()))) {
                String sourceMessageId = reads.taskSource(userId, taskId);
                if (sourceMessageId != null) {
                    finalDeliveries.close(userId, sourceMessageId, "TASK", taskId);
                }
            }
            events.publish(scope, key, "task.status_changed", Map.of("taskId", taskId, "status", task.getStatus(), "version", task.getVersion()));
        }
        return inserted;
    }
}
