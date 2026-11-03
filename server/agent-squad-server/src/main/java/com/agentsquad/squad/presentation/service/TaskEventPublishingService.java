package com.agentsquad.squad.presentation.service;

import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.conversation.service.FinalDeliveryService;
import com.agentsquad.squad.conversation.service.ConversationEventWriter;
import com.agentsquad.squad.presentation.repository.ConversationReadMapper;
import com.agentsquad.squad.common.util.RuntimePayloads;
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
    private FinalDeliveryService finalDeliveries;

    @Resource
    private TaskEventMapper taskEvents;

    @Resource
    private TaskMapper tasks;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ConversationEventWriter events;

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
        var header = reads.header(userId, scope.conversationId());
        if (header == null || header.generation() != scope.conversationGeneration()) {
            return inserted;
        }
        String key = "task-event:" + taskId + ":" + sequence;
        var task = tasks.lockOwnedRow(userId, taskId);
        if ("answer.completed".equals(eventType) || "answer.replaced".equals(eventType)) {
            try {
                var payload = RuntimePayloads.readJson(json, payloadJson);
                String text = payload.path("text").asText();
                long revision = payload.path("revision").asLong(task.answerRevision());
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
                    sequences.reserveMessage(userId, scope.conversationId(), now);
                    reads.insertTaskAnswer(messageId, userId, scope.conversationId(), sequences.messageSequence(userId, scope.conversationId()), text, revision, reads.taskSource(userId, taskId), taskId, now);
                } else {
                    // 完成事件重放不能把已修订正文恢复成旧答案；修订也只能推进消息版本。
                    if ("answer.completed".equals(eventType)
                            || reads.replaceTaskAnswer(userId, messageId, text, revision) != 1) {
                        return inserted;
                    }
                }
                events.publish(scope, key, "message.completed", Map.of("messageId", messageId, "revision", revision, "taskId", taskId, "text", text));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("目标公开答案无法读取", exception);
            }
        } else if (eventType.startsWith("interaction.")) {
            events.publish(scope, key, eventType, Map.of("taskId", taskId));
        } else {
            if ("task.completed".equals(eventType)
                    && ("CANCELLED".equals(task.status()) || "FAILED".equals(task.status()))) {
                String sourceMessageId = reads.taskSource(userId, taskId);
                if (sourceMessageId != null) {
                    finalDeliveries.close(userId, sourceMessageId, "TASK", taskId);
                }
            }
            events.publish(scope, key, "task.status_changed", Map.of("taskId", taskId, "status", task.status(), "version", task.version()));
        }
        return inserted;
    }
}
