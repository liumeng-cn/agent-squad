package com.agentsquad.squad.service;

import com.agentsquad.squad.model.bo.BindingResultBO;
import com.agentsquad.squad.model.bo.NativeProjectionCommitLogBO;

import com.agentsquad.squad.model.bo.NativeEventViewBO;

import com.agentsquad.squad.model.bo.FailureClassificationBO;
import com.agentsquad.squad.enums.GoalTaskStatus;
import com.agentsquad.squad.model.po.ExecutionScopePO;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.enums.NativeRunOutcome;
import com.agentsquad.squad.agent.MasterAgentIdentity;
import com.agentsquad.squad.model.bo.WorkerLaunchBO;
import com.agentsquad.squad.model.bo.ProjectionTargetBO;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.mapper.WorkerDispatchMapper;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.po.InteractionPO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根据 AgentScope 执行事件更新业务状态。同一 Agent 会话按事件序号依次处理，避免重复更新。
 *
 * <p>只把运行开始、结束和用户交互通知页面：interaction/requested 关联已保存的交互记录，
 * run/end 挂起时按开放交互裁决等待态；不把框架消息或单次 Run 完成直接裁决为目标成功。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NativeProjectionService {

    @Resource
    private com.agentsquad.squad.service.ExecutionAuthorizationService authorization;

    @Resource
    private ExecutionFailureService failures;

    @Resource
    private TaskEventPublishingService conversationProjection;

    @Resource
    private ResponseExecutionService responses;

    @Resource
    private ConversationEventWriterService conversationEvents;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ExecutionScopeService scopeService;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private WorkerDispatchMapper workerDispatchMapper;

    @Resource
    private WorkerResultService workerResultService;

    @Resource
    private MasterSignalService masterSignalService;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private TaskFinalizerService taskFinalizerService;

    @Resource
    private InteractionService interactionService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RuntimeAdmissionService admission;

    @Resource
    private com.agentsquad.squad.service.ActivityProjectionService activities;

    /**
     * 重复事件只跳过；缺少前序事件则回滚并等待重新读取完整前缀。
     */
    @Transactional
    public void accept(ProjectionTargetBO target, NativeEventViewBO event) {
        ExecutionScopePO scope = scopeService.lockOwned(target.getUserId(), scopeMapper.findScopeByBinding(target.getUserId(), target.getBindingId()), false);
        authorization.requireScopeCurrent(target.getUserId(), scope.getScopeId());
        Long cursor = projectionMapper.lockCursor(target.getUserId(), target.getBindingId());
        if (cursor == null) {
            throw new IllegalStateException("原生会话绑定已消失");
        }
        if (event.sequence() <= cursor) {
            return;
        }
        if (event.sequence() != cursor + 1) {
            throw new IllegalStateException("Agent执行事件中间有缺失，等待补齐后继续处理");
        }
        if ("RESPONSE".equals(scope.getKind())) {
            responses.accept(scope, target, event);
            if ("interaction/requested".equals(event.type())) {
                bindInteraction(target, event);
            } else if ("interaction/resolved".equals(event.type())) {
                resolveInteraction(target, event);
            }
        } else if (!MasterAgentIdentity.AGENT_ID.equals(target.getAgentId())) {
            acceptWorker(target, event);
        } else if ("run/start".equals(event.type())) {
            projectionMapper.markRunning(target.getUserId(), target.getTaskId());
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
                } else if (outcome == NativeRunOutcome.FAILED && failures.queueTransientRetry(scope, target.getBindingId(), event.eventId(), event.turnId())) {
                    // 原Scope内的有限恢复已持久排队，不能将其提前裁决为终态。
                } else {
                    failures.ensureNativeFailure(scope, target.getBindingId(), "native:" + event.eventId());
                    if (!failures.finishFailedTask(scope, target.getBindingId(), event.eventId())) {
                        taskFinalizerService.blockAfterNativeRun(target, event, outcome);
                    }
                }
            }
            if (outcome == NativeRunOutcome.COMPLETED
                    && !"FAILED".equals(scopeMapper.lockTaskOwner(target.getUserId(), target.getTaskId(), scope.getScopeId()).getStatus())) {
                failures.resolve(target.getUserId(), target.getBindingId());
            }
            projectionMapper.settleControls(target.getUserId(), target.getBindingId());
            masterSignalService.onMasterTurnEnded(target.getUserId(), target.getTaskId(), event.turnId());
        } else if ("interaction/requested".equals(event.type())) {
            bindInteraction(target, event);
        } else if ("interaction/resolved".equals(event.type())) {
            resolveInteraction(target, event);
        }
        if ("run/start".equals(event.type())) {
            // 新一轮 Agent 执行必须在这次接手后产生；恢复和用户答复不能当作旧回合重新开始。
            admission.recordRunStarted(target.getUserId(), target.getBindingId(), event.runId(), event.sequence());
        }
        if ("run/end".equals(event.type())) {
            if (event.turnId() != null && !event.turnId().isBlank()) {
                projectionMapper.recordEndedTurn(target.getUserId(), target.getBindingId(), event.turnId());
            }
            admission.releaseEnded(target.getUserId(), target.getBindingId(), event.turnId(), event.runId());
        }
        activities.accept(scope, target, event);
        if (projectionMapper.advanceCursor(target.getUserId(), target.getBindingId(), cursor, event.sequence()) != 1) {
            throw new IllegalStateException("Agent执行事件处理位置已被其他线程更新");
        }
        if ("run/start".equals(event.type()) || "run/end".equals(event.type())) {
            TransactionSynchronizationManager.registerSynchronization(new NativeProjectionCommitLogBO(target, event));
        }
    }

    /**
     * Worker 一次执行结束只更新自己负责的工作节点，不能直接认定整个用户任务完成。
     */
    private void acceptWorker(ProjectionTargetBO target, NativeEventViewBO event) {
        WorkerLaunchBO job = workerDispatchMapper.findByBinding(target.getUserId(), target.getBindingId());
        if (job == null || !target.getAgentId().equals(job.getAgentId())) {
            throw new IllegalStateException("Worker 原生日志与固定 Binding 不匹配");
        }
        if ("run/start".equals(event.type())) {
            publish(target, event, "job.run_started", Map.of("jobId", job.getJobId()));
        } else if ("run/end".equals(event.type())) {
            if (!"ACTIVE".equals(job.getPlanStatus())) {
                workerResultService.supersedeInFlight(target.getUserId(), target.getBindingId());
                publish(target, event, "job.run_ended", Map.of("jobId", job.getJobId(),
                        "status", "SUPERSEDED"));
            } else if (job.getCancelRequestedAt() != null) {
                // 任务取消与换计划不同；原生结束后仍由统一核对确认全部分支与外部效果。
                publish(target, event, "job.run_ended", Map.of("jobId", job.getJobId(), "status", "CANCELLING"));
            } else {
                NativeRunOutcome outcome = NativeRunOutcome.fromCode(event.outcome());
                boolean completed = outcome == NativeRunOutcome.COMPLETED
                        && workerResultService.finalizeSuccess(target.getUserId(), target.getBindingId(), event.turnId());
                if (completed) {
                    failures.resolve(target.getUserId(), target.getBindingId());
                }
                if (!completed && outcome == NativeRunOutcome.FAILED) {
                    var scope = scopeMapper.findOwned(target.getUserId(), scopeMapper.findScopeByBinding(target.getUserId(), target.getBindingId()));
                    if (failures.queueTransientRetry(scope, target.getBindingId(), event.eventId(), event.turnId())) {
                        publish(target, event, "job.recovering", Map.of("jobId", job.getJobId()));
                        return;
                    }
                }
                if (!completed) {
                    workerResultService.markRecoveryBlocked(target.getUserId(), target.getBindingId());
                }
                publish(target, event, "job.run_ended", Map.of("jobId", job.getJobId(),
                        "status", completed ? "SUCCEEDED" : "RECOVERY_BLOCKED"));
            }
        }
    }

    /**
     * 挂起回合先按开放交互转入等待态；无受控交互的挂起仍进入恢复阻塞。
     */
    private void suspendForInteraction(ProjectionTargetBO target, NativeEventViewBO event) {
        String waitingStatus = interactionService.waitingStatusOf(target.getUserId(), target.getTaskId());
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
     * 将 AgentScope 暂停执行时的交互请求关联到已保存的用户交互，并通知页面；未经过平台管理的工具暂停不产生用户交互。
     */
    private void bindInteraction(ProjectionTargetBO target, NativeEventViewBO event) {
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        if (!(data.get("requestId") instanceof String requestId) || requestId.isBlank()) {
            log.warn("暂停请求缺少 requestId，无法关联用户交互 bindingId={} event={}",
                    target.getBindingId(), event.eventId());
            return;
        }
        Map<String, Object> call = data.get("call") instanceof Map<?, ?> rawCall
                ? (Map<String, Object>) rawCall : Map.of();
        BindingResultBO binding = interactionService.bindFrameworkRequest(
                target.getUserId(), target.getTaskId(), target.getBindingId(), null, event.turnId(),
                requestId, call);
        if (binding == null) {
            return;
        }
        var scope = scopeMapper.findOwned(target.getUserId(), scopeMapper.findScopeByBinding(target.getUserId(), target.getBindingId()));
        boolean approval = "APPROVAL".equals(binding.kind());
        failures.record(scope, target.getBindingId(), "interaction:" + binding.interactionId(),
                new FailureClassificationBO(approval ? "AUTH_REQUIRED" : "INPUT_REQUIRED",
                        approval ? "USER_CONFIRMATION_REQUIRED" : "USER_INPUT_NEEDED", "USER",
                        approval ? "有一项操作需要你确认影响后决定是否执行。" : "需要你补充一些信息，才能继续。"));
        publish(target, event, "interaction.requested",
                Map.of("interactionId", binding.interactionId(), "kind", binding.kind()));
    }

    /**
     * 框架侧解析后发布公开事件；交互行不存在说明该请求未受控。
     */
    private void resolveInteraction(ProjectionTargetBO target, NativeEventViewBO event) {
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        if (!(data.get("requestId") instanceof String requestId) || requestId.isBlank()) {
            return;
        }
        InteractionPO row = interactionService.resolveFromFramework(
                target.getUserId(), target.getBindingId(), requestId);
        if (row == null) {
            return;
        }
        failures.resolveInteraction(target.getUserId(), row.getScopeId(), row.getInteractionId());
        publish(target, event, "interaction.resolved",
                Map.of("interactionId", row.getInteractionId(), "status", row.getStatus()));
    }

    private void publish(ProjectionTargetBO target, NativeEventViewBO event, String eventType, Object payloadExtra) {
        if (target.getTaskId() == null) {
            ExecutionScopePO scope = scopeMapper.findOwned(target.getUserId(), scopeMapper.findScopeByBinding(target.getUserId(), target.getBindingId()));
            conversationEvents.publish(scope, "native:" + target.getBindingId() + ":" + event.eventId(), eventType, payloadExtra);
            return;
        }
        if (projectionMapper.reserveEventSequence(target.getUserId(), target.getTaskId()) != 1) {
            throw new IllegalStateException("目标已消失");
        }
        Long sequence = projectionMapper.eventSequence(target.getUserId(), target.getTaskId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", target.getTaskId());
        payload.put("bindingId", target.getBindingId());
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
        conversationProjection.insertProjected(target.getUserId(), target.getTaskId(), sequence,
                "native:" + target.getBindingId() + ":" + event.eventId(), eventType,
                RuntimePayloads.toJson(objectMapper, payload), occurredAt);
    }
}
