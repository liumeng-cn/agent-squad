package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.GoalTaskStatus;
import com.agentsquad.squad.runtime.domain.NativeRunOutcome;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.repository.TaskEventMapper;
import com.agentsquad.squad.runtime.service.TaskFinalizerService;
import com.agentsquad.squad.runtime.util.RuntimePayloads;
import com.agentsquad.squad.tools.interaction.BindingResult;
import com.agentsquad.squad.tools.interaction.InteractionService;
import com.agentsquad.squad.tools.interaction.domain.InteractionRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原生事件到业务进度的事务投影。按 Binding 行锁串行化水位和公开事件序号。
 *
 * <p>仅发布运行生命周期与受控交互：interaction/requested 绑定持久交互，
 * run/end 挂起时按开放交互裁决等待态；不把框架消息或单次 Run 完成直接裁决为目标成功。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NativeProjectionService {

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private TaskFinalizerService taskFinalizerService;

    @Resource
    private InteractionService interactionService;

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
            NativeRunOutcome outcome = NativeRunOutcome.fromCode(event.outcome());
            publish(target, event, "task.run_ended", outcome.code());
            if (outcome == NativeRunOutcome.COMPLETED) {
                if (!taskFinalizerService.tryComplete(target, event)
                        && !taskFinalizerService.tryCancelAfterStop(target, event)) {
                    taskFinalizerService.recoverMissingProposal(target, event);
                }
            } else if (!taskFinalizerService.tryCancelAfterStop(target, event)) {
                if (outcome == NativeRunOutcome.SUSPENDED) {
                    suspendForInteraction(target, event);
                } else {
                    taskFinalizerService.blockAfterNativeRun(target, event, outcome);
                }
            }
        } else if ("interaction/requested".equals(event.type())) {
            bindInteraction(target, event);
        } else if ("interaction/resolved".equals(event.type())) {
            resolveInteraction(target, event);
        }
        if (projectionMapper.advanceCursor(target.userId(), target.bindingId(), cursor, event.sequence()) != 1) {
            throw new IllegalStateException("原生日志投影水位竞争");
        }
    }

    /**
     * 挂起回合先按开放交互转入等待态；无受控交互的挂起仍进入恢复阻塞。
     */
    private void suspendForInteraction(ProjectionTarget target, NativeEventView event) {
        String waitingStatus = interactionService.waitingStatusOf(target.userId(), target.taskId());
        if (waitingStatus == null) {
            taskFinalizerService.blockAfterNativeRun(target, event, NativeRunOutcome.SUSPENDED);
            return;
        }
        String reason = GoalTaskStatus.WAITING_APPROVAL.name().equals(waitingStatus)
                ? "写入操作等待用户在页面上审批"
                : "追问等待用户在页面上答复";
        taskFinalizerService.waitForInteraction(target, event, waitingStatus, reason);
    }

    /**
     * 绑定框架挂起请求并发布公开事件；非受控工具挂起不产生交互。
     */
    private void bindInteraction(ProjectionTarget target, NativeEventView event) {
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        if (!(data.get("requestId") instanceof String requestId) || requestId.isBlank()) {
            log.warn("挂起请求缺少 requestId，跳过绑定 bindingId={} event={}",
                    target.bindingId(), event.eventId());
            return;
        }
        Map<String, Object> call = data.get("call") instanceof Map<?, ?> rawCall
                ? (Map<String, Object>) rawCall : Map.of();
        BindingResult binding = interactionService.bindFrameworkRequest(
                target.userId(), target.taskId(), target.bindingId(), null, event.turnId(),
                requestId, call);
        if (binding == null) {
            return;
        }
        publish(target, event, "interaction.requested",
                Map.of("interactionId", binding.interactionId(), "kind", binding.kind()));
    }

    /**
     * 框架侧解析后发布公开事件；交互行不存在说明该请求未受控。
     */
    private void resolveInteraction(ProjectionTarget target, NativeEventView event) {
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        if (!(data.get("requestId") instanceof String requestId) || requestId.isBlank()) {
            return;
        }
        InteractionRow row = interactionService.resolveFromFramework(
                target.userId(), target.bindingId(), requestId);
        if (row == null) {
            return;
        }
        publish(target, event, "interaction.resolved",
                Map.of("interactionId", row.interactionId(), "status", row.status()));
    }

    private void publish(ProjectionTarget target, NativeEventView event, String eventType, Object payloadExtra) {
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
        if (payloadExtra instanceof Map<?, ?> extra) {
            payload.putAll((Map<String, Object>) extra);
        } else if (payloadExtra != null) {
            payload.put("outcome", payloadExtra);
        }
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.occurredAtMillis()), ZoneOffset.UTC);
        taskEventMapper.insertProjected(target.userId(), target.taskId(), sequence,
                "native:" + target.bindingId() + ":" + event.eventId(), eventType,
                RuntimePayloads.toJson(objectMapper, payload), occurredAt);
    }
}
