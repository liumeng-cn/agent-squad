package com.agentsquad.squad.service;

import com.agentsquad.squad.bo.FinalizationBO;
import com.agentsquad.squad.enums.GoalTaskStatus;
import com.agentsquad.squad.enums.PlanJoinStage;
import com.agentsquad.squad.enums.RuntimeCommandAction;
import com.agentsquad.squad.bo.FailureClassificationBO;

import com.agentsquad.squad.enums.NativeRunOutcome;

import com.agentsquad.squad.bo.AcceptanceReviewBO;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.bo.NativeEventViewBO;
import com.agentsquad.squad.bo.ProjectionTargetBO;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.mapper.CompletionMapper;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 目标终态裁决。仅接受同一 Master Binding/Turn 的持久完成候选与成功的原生回合事实。
 *
 * <p>由原生事件投影事务调用；终态、最终答案事件、会话占用释放一起提交或一起回滚。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class TaskFinalizerService {

    private static final Set<String> ALLOWED_OUTCOMES = Set.of(
            GoalTaskStatus.SUCCEEDED.name(), GoalTaskStatus.PARTIAL.name(), GoalTaskStatus.FAILED.name());
    private static final String CORRECTION_MESSAGE = "当前目标尚未提交完成候选。请重新检查用户目标，"
            + "然后调用 propose_completion 提交完整回答；若无法完整回答，可选择 PARTIAL 并说明缺口。";
    private static final String MISSING_PROPOSAL_ANSWER = "本次目标未能生成有效的最终回答，请重新发起。";

    @Resource
    private com.agentsquad.squad.agent.session.NativeSessionGateway nativeGateway;

    @Resource
    private ExecutionFailureService failures;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private TaskEventPublishingService conversationProjection;

    @Resource
    private TaskDirectiveMapper directives;

    @Resource
    private CompletionMapper completionMapper;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PlanCompletionEvaluationService planEvaluation;

    /**
     * 原生成功回合只接受当前 Binding、Turn 和版本的候选；失效候选阻塞恢复并推进投影水位。
     */
    @Transactional
    public boolean tryComplete(ProjectionTargetBO target, NativeEventViewBO event) {
        if (!directives.pending(target.getUserId(), target.getTaskId()).isEmpty()) {
            return false;
        }
        FinalizationBO row = completionMapper.lockTask(target.getUserId(), target.getTaskId());
        if (row == null || !GoalTaskStatus.RUNNING.name().equals(row.getStatus()) || row.isCancellationRequested()
                || row.getProposalJson() == null) {
            return false;
        }
        if (!target.getBindingId().equals(row.getProposalBindingId())
                || event.turnId() == null || !event.turnId().equals(row.getProposalTurnId())) {
            log.debug("忽略不属于当前完成候选的迟到回合 taskId={} bindingId={} turnId={}",
                    row.getTaskId(), target.getBindingId(), event.turnId());
            return false;
        }
        JsonNode proposal;
        try {
            proposal = parseProposal(row.getProposalJson());
        } catch (IllegalStateException exception) {
            blockInvalidProposal(row, target);
            return true;
        }
        String outcome = proposal.path("outcome").asText();
        String answer = proposal.path("finalAnswer").asText();
        if (!ALLOWED_OUTCOMES.contains(outcome) || answer.isBlank()
                || !proposal.path("evidenceRefs").isArray()
                || proposal.path("planVersion").asLong(-1) != row.getPlanVersion()
                || proposal.path("answerRevision").asLong(-1) != row.getAnswerRevision()
                || !target.getBindingId().equals(proposal.path("sourceBindingId").asText())
                || !event.turnId().equals(proposal.path("sourceTurnId").asText())) {
            blockInvalidProposal(row, target);
            return true;
        }
        if (row.getPlanVersion() > 0) {
            try {
                List<AcceptanceReviewBO> reviews = objectMapper.convertValue(proposal.path("acceptanceReviews"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                AcceptanceReviewBO.class));
                var evaluation = planEvaluation.evaluate(row.getUserId(), row.getTaskId(), row.getPlanVersion(), reviews);
                if (evaluation.decision().stage() != PlanJoinStage.READY
                        || (GoalTaskStatus.SUCCEEDED.name().equals(outcome)
                        && evaluation.decision().outcome() != GoalTaskStatus.SUCCEEDED)
                        || (GoalTaskStatus.PARTIAL.name().equals(outcome)
                        && evaluation.decision().outcome() == GoalTaskStatus.FAILED)) {
                    blockInvalidProposal(row, target);
                    return true;
                }
            } catch (RuntimeException exception) {
                if (!(exception instanceof com.agentsquad.squad.exceptions.RuntimeBusinessException)
                        && !(exception instanceof IllegalArgumentException)) {
                    throw exception;
                }
                blockInvalidProposal(row, target);
                return true;
            }
        }
        if (completionMapper.completeTask(row.getUserId(), row.getTaskId(), target.getBindingId(),
                event.turnId(), outcome, row.getPlanVersion(), row.getAnswerRevision()) != 1) {
            throw new IllegalStateException("目标终态写入竞争");
        }
        if (conversationMapper.clearActiveTask(row.getUserId(), row.getConversationId(), row.getTaskId()) != 1) {
            throw new IllegalStateException("对话目标占用与终态不一致");
        }
        String producerPrefix = "completion:" + row.getTaskId() + ":" + event.turnId();
        publish(row, producerPrefix + ":answer.completed", "answer.completed",
                Map.of("text", answer, "revision", row.getAnswerRevision() + 1, "evidenceRefs", List.of()));
        publish(row, producerPrefix + ":task.completed", "task.completed",
                Map.of("status", outcome, "version", row.getVersion() + 1));
        log.info("目标已终结 taskId={} bindingId={} turnId={} outcome={}",
                row.getTaskId(), target.getBindingId(), event.turnId(), outcome);
        return true;
    }

    /**
     * 候选已持久化但无法安全发布时显式阻塞，防止同一原生结束事件无限回放。
     */
    private void blockInvalidProposal(FinalizationBO row, ProjectionTargetBO target) {
        String reason = "完成候选校验未通过，需核对后恢复";
        if (completionMapper.blockForRecovery(row.getUserId(), row.getTaskId(), reason) != 1) {
            throw new IllegalStateException("失效完成候选阻塞状态写入竞争");
        }
        publish(row, "recovery:invalid-proposal:" + target.getBindingId() + ":" + row.getProposalTurnId(),
                "task.recovery_blocked", Map.of("reason", reason));
        log.warn("完成候选失效，目标等待恢复核对 taskId={} bindingId={}", row.getTaskId(), target.getBindingId());
    }

    /**
     * 停止意图存在且原生回合已结束时收尾为 CANCELLED；无取消意图的任务保持原状等待正常完成。
     */
    @Transactional
    public boolean tryCancelAfterStop(ProjectionTargetBO target, NativeEventViewBO event) {
        return finalizeCancelled(target.getUserId(), target.getTaskId(),
                "native:" + target.getBindingId() + ":" + event.turnId() + ":task.completed");
    }

    /**
     * 原生回合正常结束但没有候选时，第一次持久投递纠正命令；第二次明确失败并释放对话。
     * 调用方的原生投影事务同时保存命令、公开事件和水位，崩溃重放不会重复生成纠正回合。
     */
    @Transactional
    public boolean recoverMissingProposal(ProjectionTargetBO target, NativeEventViewBO event) {
        if (!directives.pending(target.getUserId(), target.getTaskId()).isEmpty()) {
            return true;
        }
        FinalizationBO row = completionMapper.lockTask(target.getUserId(), target.getTaskId());
        if (row == null || !GoalTaskStatus.RUNNING.name().equals(row.getStatus()) || row.isCancellationRequested()
                || row.getProposalJson() != null || event.turnId() == null) {
            return false;
        }
        // 旧计划的汇总回合可能刚发布新版；它不能代替新版完成通知，更不能消耗完成纠正次数。
        if (row.getPlanVersion() > 0
                && planMapper.countActivePlan(row.getUserId(), row.getTaskId(), row.getPlanVersion()) > 0
                && commandMapper.countPlanSignalTurn(row.getUserId(), row.getTaskId(), event.turnId(), row.getPlanVersion()) == 0) {
            log.info("Master 规划回合已结束，等待 Worker 汇合 taskId={} planVersion={} turnId={}",
                    row.getTaskId(), row.getPlanVersion(), event.turnId());
            return true;
        }
        if (row.getCorrectionCount() == 0) {
            if (completionMapper.markCorrectionRequested(row.getUserId(), row.getTaskId()) != 1) {
                throw new IllegalStateException("完成纠正次数写入竞争");
            }
            String commandId = UUID.randomUUID().toString();
            String payloadJson = RuntimePayloads.toJson(objectMapper, Map.of("message", CORRECTION_MESSAGE));
            if (commandMapper.insertControl(commandId, row.getUserId(),
                    "task.completion-correction:" + row.getTaskId(), event.turnId(),
                    ContentDigests.sha256(payloadJson), row.getTaskId(), target.getBindingId(),
                    RuntimeCommandAction.SUBMIT.name(), payloadJson,
                    LocalDateTime.now(ZoneOffset.UTC)) != 1) {
                throw new IllegalStateException("完成纠正命令写入失败");
            }
            log.info("完成候选缺失，已排入一次纠正 taskId={} bindingId={} turnId={} commandId={}",
                    row.getTaskId(), target.getBindingId(), event.turnId(), commandId);
            return true;
        }
        if (row.getCorrectionCount() != 1) {
            throw new IllegalStateException("完成纠正次数超出约束");
        }
        if (completionMapper.failWithoutProposal(row.getUserId(), row.getTaskId()) != 1) {
            throw new IllegalStateException("无完成候选的任务终态写入竞争");
        }
        if (conversationMapper.clearActiveTask(row.getUserId(), row.getConversationId(), row.getTaskId()) != 1) {
            throw new IllegalStateException("对话目标占用与失败终态不一致");
        }
        var scope = scopes.lockOwned(row.getUserId(), scopes.scopeForSession(row.getUserId(), target.getSessionId()), false);
        failures.record(scope, target.getBindingId(), "completion-correction-exhausted:" + event.turnId(),
                new FailureClassificationBO("LOGIC_INVALID", "COMPLETION_CORRECTION_EXHAUSTED", "SYSTEM",
                        "本次未能形成有效的完整结果，已保留你的内容。"));
        String producerPrefix = "completion:" + row.getTaskId() + ":" + event.turnId();
        publish(row, producerPrefix + ":answer.completed", "answer.completed",
                Map.of("text", MISSING_PROPOSAL_ANSWER, "revision", row.getAnswerRevision() + 1, "evidenceRefs", List.of()));
        publish(row, producerPrefix + ":task.completed", "task.completed",
                Map.of("status", GoalTaskStatus.FAILED.name(), "version", row.getVersion() + 1));
        log.warn("纠正后仍无完成候选，目标已失败 taskId={} bindingId={} turnId={}",
                row.getTaskId(), target.getBindingId(), event.turnId());
        return true;
    }

    /**
     * 原生回合未成功且用户未取消时阻塞原任务，清除该失败回合的完成候选。
     * 原因只记录平台归一化文案，避免把模型或框架异常原文暴露给页面。
     */
    @Transactional
    public boolean blockAfterNativeRun(ProjectionTargetBO target, NativeEventViewBO event,
                                       NativeRunOutcome outcome) {
        FinalizationBO row = completionMapper.lockTask(target.getUserId(), target.getTaskId());
        if (row == null || row.isCancellationRequested()
                || !GoalTaskStatus.RUNNING.name().equals(row.getStatus())
                && !GoalTaskStatus.QUEUED.name().equals(row.getStatus())) {
            return false;
        }
        String reason = switch (outcome) {
            case FAILED -> "原生回合执行失败，需核对后恢复";
            case SUSPENDED -> "原生回合已挂起，当前版本无法继续此类交互";
            case INTERRUPTED -> "原生回合意外中断，需核对后恢复";
            case UNKNOWN -> "原生回合状态未知，需核对后恢复";
            case COMPLETED -> throw new IllegalArgumentException("成功回合不得进入恢复阻塞");
        };
        if (completionMapper.blockForRecovery(row.getUserId(), row.getTaskId(), reason) != 1) {
            throw new IllegalStateException("恢复阻塞状态写入竞争");
        }
        publish(row, "recovery:" + target.getBindingId() + ":" + event.eventId(),
                "task.recovery_blocked", Map.of("reason", reason, "nativeOutcome", outcome.code()));
        log.warn("原生回合未成功，目标等待恢复核对 taskId={} bindingId={} turnId={} outcome={}",
                row.getTaskId(), target.getBindingId(), event.turnId(), outcome.code());
        return true;
    }

    /**
     * 原生回合因受控交互挂起且已有开放交互时，任务进入对应等待态；
     * 页面答复经 RESPOND 命令回填后由新一轮原生事件继续裁决。
     */
    @Transactional
    public boolean waitForInteraction(ProjectionTargetBO target, NativeEventViewBO event,
                                      String status, String reason) {
        FinalizationBO row = completionMapper.lockTask(target.getUserId(), target.getTaskId());
        if (row == null || row.isCancellationRequested()
                || !GoalTaskStatus.RUNNING.name().equals(row.getStatus())
                && !GoalTaskStatus.QUEUED.name().equals(row.getStatus())) {
            return false;
        }
        if (completionMapper.waitForInteraction(row.getUserId(), row.getTaskId(), status, reason) != 1) {
            throw new IllegalStateException("交互等待状态写入竞争");
        }
        publish(row, "recovery:interaction:" + target.getBindingId() + ":" + event.eventId(),
                "task.state_changed", Map.of("status", status, "version", row.getVersion() + 1));
        log.info("目标等待用户交互 taskId={} bindingId={} status={} interactionEvent={}",
                row.getTaskId(), target.getBindingId(), status, event.eventId());
        return true;
    }

    /**
     * 交互答复投递前把等待态目标转回 RUNNING，让恢复回合可以重新提交完成候选。
     * 单条 CAS 自动提交；已不处于等待态时静默跳过，由回合结束裁决兜底。
     */
    public void resumeFromInteraction(String userId, String taskId) {
        if (completionMapper.resumeFromInteraction(userId, taskId) == 1) {
            FinalizationBO row = completionMapper.lockTask(userId, taskId);
            if (row != null) {
                publish(row, "control:interaction-resume:" + taskId + ":" + row.getVersion(),
                        "task.state_changed", Map.of("status", GoalTaskStatus.RUNNING.name(),
                                "version", row.getVersion()));
                log.info("交互答复后目标恢复运行 taskId={}", taskId);
            }
        }
    }

    /**
     * 停止投递时发现原生会话已无活跃运行，就地取消目标并释放会话占用。
     */
    @Transactional
    public void finalizeCancelledTask(String userId, String taskId) {
        finalizeCancelled(userId, taskId, "stop-delivery:" + taskId + ":task.completed");
    }

    private boolean finalizeCancelled(String userId, String taskId, String producerKey) {
        scopes.lockOwned(userId, scopes.scopeForTask(userId, taskId), false);
        FinalizationBO row = completionMapper.lockTask(userId, taskId);
        if (row == null || !row.isCancellationRequested()
                || !GoalTaskStatus.RUNNING.name().equals(row.getStatus())
                && !GoalTaskStatus.CANCELLING.name().equals(row.getStatus())) {
            return false;
        }
        if (!completionMapper.lockCancellationOperations(userId, taskId).isEmpty()
                || !completionMapper.lockCancellationSandboxes(userId, taskId).isEmpty()
                || !completionMapper.lockCancellationInteractions(userId, taskId).isEmpty()) {
            return false;
        }
        var targets = completionMapper.cancellationTargets(userId, taskId);
        if (targets.isEmpty() || targets.stream().anyMatch(target -> !nativeGateway.quiescent(target))) {
            return false;
        }
        completionMapper.cancelJobs(userId, taskId);
        if (completionMapper.cancelTask(row.getUserId(), row.getTaskId()) != 1) {
            return false;
        }
        if (conversationMapper.clearActiveTask(row.getUserId(), row.getConversationId(), row.getTaskId()) != 1) {
            throw new IllegalStateException("对话目标占用与取消终态不一致");
        }
        publish(row, producerKey, "task.completed",
                Map.of("status", GoalTaskStatus.CANCELLED.name(), "version", row.getVersion() + 1));
        log.info("目标已随停止意图取消 taskId={} userId={}", row.getTaskId(), userId);
        return true;
    }

    private void publish(FinalizationBO row, String producerKey, String type, Object payload) {
        if (projectionMapper.reserveEventSequence(row.getUserId(), row.getTaskId()) != 1) {
            throw new IllegalStateException("最终事件序号保留失败");
        }
        Long sequence = projectionMapper.eventSequence(row.getUserId(), row.getTaskId());
        if (sequence == null) {
            throw new IllegalStateException("最终事件序号读取失败");
        }
        conversationProjection.insertProjected(row.getUserId(), row.getTaskId(), sequence,
                producerKey, type, RuntimePayloads.toJson(objectMapper, payload),
                LocalDateTime.now(ZoneOffset.UTC));
    }

    private JsonNode parseProposal(String value) {
        try {
            return RuntimePayloads.readJson(objectMapper, value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("持久完成候选无法解析", exception);
        }
    }
}
