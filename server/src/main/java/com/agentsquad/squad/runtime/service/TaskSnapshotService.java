package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.domain.EventRow;
import com.agentsquad.squad.runtime.domain.TaskRow;
import com.agentsquad.squad.runtime.dto.AnswerView;
import com.agentsquad.squad.runtime.dto.TaskResponse;
import com.agentsquad.squad.runtime.dto.TaskSnapshot;
import com.agentsquad.squad.runtime.repository.TaskEventMapper;
import com.agentsquad.squad.runtime.repository.TaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

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
    private ObjectMapper objectMapper;

    /**
     * 读取当前用户单个任务快照；任务不存在时统一按 NOT_FOUND 处理。
     *
     * <p>答案文本取最新的 answer.completed 公开事件；无该事件时返回未完成的占位视图。
     * 用户目标原文从规范化输入投影，供刷新后只读恢复聊天正文。</p>
     */
    public TaskSnapshot snapshot(String userId, String taskId) {
        TaskRow task = taskMapper.findOwned(userId, taskId);
        if (task == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        long maxSeq = taskEventMapper.maxSeq(userId, taskId);
        TaskResponse taskResponse = new TaskResponse(task.taskId(), task.conversationId(), task.status(),
                task.version(), task.planVersion(), task.answerRevision(),
                formatTime(task.cancelRequestedAt()), null,
                formatTime(task.createTime()), formatTime(task.lastUpdateTime()));
        AnswerView answer = buildAnswer(userId, taskId, task.answerRevision());
        String cursor = taskId + ":" + maxSeq;
        log.debug("任务快照生成 userId={} taskId={} seq={} answerCompleted={}",
                userId, taskId, maxSeq, answer.completed());
        return new TaskSnapshot(taskResponse, goalMessage(userId, taskId),
                Collections.emptyList(), Collections.emptyList(), answer, cursor);
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
            return parseJsonValue(inputJson).path("message").asText("");
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
            JsonNode payload = parseJsonValue(event.payloadJson());
            String text = payload.path("text").asText("");
            return new AnswerView(answerRevision, text, true);
        } catch (JsonProcessingException exception) {
            log.warn("答案事件载荷无法解析 userId={} taskId={}", userId, taskId, exception);
            return new AnswerView(answerRevision, "", false);
        }
    }

    /**
     * 兼容 MySQL 与 H2 对 JSON 列的读取差异：H2 可能把 JSON 对象存成 JSON 字符串返回。
     */
    private JsonNode parseJsonValue(String raw) throws JsonProcessingException {
        JsonNode first = objectMapper.readTree(raw);
        if (first.isTextual()) {
            return objectMapper.readTree(first.asText());
        }
        return first;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
