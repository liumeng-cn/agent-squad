package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.repository.TaskEventMapper;
import com.agentsquad.squad.runtime.service.TaskFinalizerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 原生事件到业务进度的事务投影。按 Binding 行锁串行化水位和公开事件序号。
 *
 * <p>仅发布运行生命周期，不把框架消息或单次 Run 完成直接裁决为目标成功。</p>
 */
@Service
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NativeProjectionService {

    private static final Set<String> OUTCOMES = Set.of("completed", "failed", "suspended", "interrupted");

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private TaskFinalizerService taskFinalizerService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 重复事件只跳过；缺少前序事件则回滚并等待重新读取完整前缀。
     */
    @Transactional
    public void accept(ProjectionTarget target, NativeEventView event) {
        Long cursor = projectionMapper.lockCursor(target.userId(), target.bindingId());
        if (cursor == null) {
            throw new IllegalStateException("原生会话绑定已消失");
        }
        if (event.sequence() <= cursor) {
            return;
        }
        if (event.sequence() != cursor + 1) {
            throw new IllegalStateException("原生日志事件序号不连续");
        }
        if ("run/start".equals(event.type())) {
            projectionMapper.markRunning(target.userId(), target.taskId());
            publish(target, event, "task.run_started", null);
        } else if ("run/end".equals(event.type())) {
            String outcome = event.outcome() != null && OUTCOMES.contains(event.outcome())
                    ? event.outcome() : "unknown";
            publish(target, event, "task.run_ended", outcome);
            if ("completed".equals(outcome)) {
                taskFinalizerService.tryComplete(target, event);
            }
        }
        if (projectionMapper.advanceCursor(target.userId(), target.bindingId(), cursor, event.sequence()) != 1) {
            throw new IllegalStateException("原生日志投影水位竞争");
        }
    }

    private void publish(ProjectionTarget target, NativeEventView event, String eventType, String outcome) {
        if (projectionMapper.reserveEventSequence(target.userId(), target.taskId()) != 1) {
            throw new IllegalStateException("目标已消失");
        }
        Long sequence = projectionMapper.eventSequence(target.userId(), target.taskId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", target.taskId());
        payload.put("bindingId", target.bindingId());
        payload.put("nativeSequence", event.sequence());
        if (event.runId() != null) {
            payload.put("runId", event.runId());
        }
        if (outcome != null) {
            payload.put("outcome", outcome);
        }
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.occurredAtMillis()), ZoneOffset.UTC);
        taskEventMapper.insertProjected(target.userId(), target.taskId(), sequence,
                "native:" + target.bindingId() + ":" + event.eventId(), eventType,
                toJson(payload), occurredAt);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("公开事件无法序列化", exception);
        }
    }
}
