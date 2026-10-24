package com.agentsquad.squad.presentation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

import com.agentsquad.squad.runtime.domain.EventRow;
import com.agentsquad.squad.conversation.domain.TaskRow;
import com.agentsquad.squad.presentation.dto.AnswerView;
import com.agentsquad.squad.presentation.dto.Interaction;
import com.agentsquad.squad.conversation.dto.TaskResponse;
import com.agentsquad.squad.presentation.dto.TaskSnapshot;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.interaction.service.InteractionQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 任务快照服务；为详情页聚合目标状态、事件游标及当前答案占位。
 *
 * <p>D1 直接回答阶段无 Worker 任务与交互项，jobs 与 pending 恒为空；答案视图由 Bridge 后续写入。</p>
 */
@Service
@Slf4j
public class TaskSnapshotService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private InteractionQueryService interactionQueryService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 读取当前用户单个任务快照；任务不存在时统一按 NOT_FOUND 处理。
     *
     * <p>答案文本取最新的 answer.completed 公开事件；无该事件时返回未完成的占位视图。
     * pending 取开放的持久交互，供详情页渲染追问卡与审批卡。</p>
     */
    public TaskSnapshot snapshot(String userId, String taskId) {
        TaskRow task = taskMapper.findOwned(userId, taskId);
        if (task == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        long maxSeq = taskEventMapper.maxSeq(userId, taskId);
        TaskResponse taskResponse = new TaskResponse(task.taskId(), task.conversationId(), task.status(),
                task.version(), task.planVersion(), task.answerRevision(),
                formatTime(task.cancelRequestedAt()), task.blockedReason(),
                formatTime(task.createTime()), formatTime(task.lastUpdateTime()));
        AnswerView answer = buildAnswer(userId, taskId, task.answerRevision());
        List<Interaction> pending = interactionQueryService.listOpenByTask(userId, taskId).stream()
                .map(row -> interactionQueryService.viewOf(userId, row))
                .toList();
        String cursor = taskId + ":" + maxSeq;
        log.debug("任务快照生成 userId={} taskId={} seq={} answerCompleted={} pending={}",
                userId, taskId, maxSeq, answer.completed(), pending.size());
        return new TaskSnapshot(taskResponse, goalMessage(userId, taskId),
                Collections.emptyList(), pending, answer, cursor);
    }

    /**
     * 从规范化输入投影用户目标原文；历史数据缺失 message 时返回空串，不抛错。
     */
    private String goalMessage(String userId, String taskId) {
        String inputJson = taskMapper.findInputJson(userId, taskId);
        if (inputJson == null || inputJson.isBlank()) {
            return "";
        }
        try {
            return RuntimePayloads.readJson(objectMapper, inputJson).path("message").asText("");
        } catch (JsonProcessingException exception) {
            log.warn("目标输入载荷无法解析 userId={} taskId={}", userId, taskId, exception);
            return "";
        }
    }

    private AnswerView buildAnswer(String userId, String taskId, long answerRevision) {
        EventRow event = taskEventMapper.findLatestAnswerEvent(userId, taskId);
        if (event == null) {
            return new AnswerView(answerRevision, "", false);
        }
        try {
            JsonNode payload = RuntimePayloads.readJson(objectMapper, event.payloadJson());
            String text = payload.path("text").asText("");
            return new AnswerView(answerRevision, text, true);
        } catch (JsonProcessingException exception) {
            log.warn("答案事件载荷无法解析 userId={} taskId={}", userId, taskId, exception);
            return new AnswerView(answerRevision, "", false);
        }
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
