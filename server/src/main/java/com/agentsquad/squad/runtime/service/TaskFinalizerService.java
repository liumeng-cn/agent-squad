package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.bridge.NativeEventView;
import com.agentsquad.squad.runtime.domain.FinalizationRow;
import com.agentsquad.squad.runtime.domain.GoalTaskStatus;
import com.agentsquad.squad.runtime.domain.NativeRunOutcome;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.domain.RuntimeCommandAction;
import com.agentsquad.squad.runtime.repository.CompletionMapper;
import com.agentsquad.squad.runtime.repository.CommandMapper;
import com.agentsquad.squad.runtime.repository.ConversationMapper;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.repository.TaskEventMapper;
import com.agentsquad.squad.runtime.util.ContentDigests;
import com.agentsquad.squad.runtime.util.RuntimePayloads;
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
            GoalTaskStatus.SUCCEEDED.name(), GoalTaskStatus.PARTIAL.name());
    private static final String CORRECTION_MESSAGE = "当前目标尚未提交完成候选。请重新检查用户目标，"
            + "然后调用 propose_completion 提交完整回答；若无法完整回答，可选择 PARTIAL 并说明缺口。";
    private static final String MISSING_PROPOSAL_ANSWER = "本次目标未能生成有效的最终回答，请重新发起。";

    @Resource
    private CompletionMapper completionMapper;

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

    /**
     * 没有候选、来源不符或取消中时保持任务原状；写入失败则抛异常回滚投影水位，等待重试。
     */
    @Transactional
    public boolean tryComplete(ProjectionTarget target, NativeEventView event) {
        FinalizationRow row = completionMapper.lockTask(target.userId(), target.taskId());
        if (row == null || !GoalTaskStatus.RUNNING.name().equals(row.status()) || row.cancellationRequested()
                || row.proposalJson() == null || !target.bindingId().equals(row.proposalBindingId())
                || event.turnId() == null || !event.turnId().equals(row.proposalTurnId())) {
            return false;
        }
        JsonNode proposal = parseProposal(row.proposalJson());
        String outcome = proposal.path("outcome").asText();
        String answer = proposal.path("finalAnswer").asText();
        if (!ALLOWED_OUTCOMES.contains(outcome) || answer.isBlank()) {
            throw new IllegalStateException("持久完成候选不满足终态契约");
        }
        if (!proposal.path("evidenceRefs").isArray()
                || proposal.path("planVersion").asLong(-1) != row.planVersion()
                || proposal.path("answerRevision").asLong(-1) != row.answerRevision()
                || !target.bindingId().equals(proposal.path("sourceBindingId").asText())
                || !event.turnId().equals(proposal.path("sourceTurnId").asText())) {
            throw new IllegalStateException("完成候选的来源或版本已失效");
        }
        if (completionMapper.completeTask(row.userId(), row.taskId(), target.bindingId(),
                event.turnId(), outcome, row.planVersion(), row.answerRevision()) != 1) {
            throw new IllegalStateException("目标终态写入竞争");
        }
        if (conversationMapper.clearActiveTask(row.userId(), row.conversationId(), row.taskId()) != 1) {
            throw new IllegalStateException("对话目标占用与终态不一致");
        }
        String producerPrefix = "completion:" + row.taskId() + ":" + event.turnId();
        publish(row, producerPrefix + ":answer.completed", "answer.completed",
                Map.of("text", answer, "evidenceRefs", List.of()));
        publish(row, producerPrefix + ":task.completed", "task.completed",
                Map.of("status", outcome, "version", row.version() + 1));
        log.info("目标已终结 taskId={} bindingId={} turnId={} outcome={}",
                row.taskId(), target.bindingId(), event.turnId(), outcome);
        return true;
    }

    /**
     * 停止意图存在且原生回合已结束时收尾为 CANCELLED；无取消意图的任务保持原状等待正常完成。
     */
    @Transactional
    public boolean tryCancelAfterStop(ProjectionTarget target, NativeEventView event) {
        return finalizeCancelled(target.userId(), target.taskId(),
                "native:" + target.bindingId() + ":" + event.turnId() + ":task.completed");
    }

    /**
     * 原生回合正常结束但没有候选时，第一次持久投递纠正命令；第二次明确失败并释放对话。
     * 调用方的原生投影事务同时保存命令、公开事件和水位，崩溃重放不会重复生成纠正回合。
     */
    @Transactional
    public boolean recoverMissingProposal(ProjectionTarget target, NativeEventView event) {
        FinalizationRow row = completionMapper.lockTask(target.userId(), target.taskId());
        if (row == null || !GoalTaskStatus.RUNNING.name().equals(row.status()) || row.cancellationRequested()
                || row.proposalJson() != null || event.turnId() == null) {
            return false;
        }
        if (row.correctionCount() == 0) {
            if (completionMapper.markCorrectionRequested(row.userId(), row.taskId()) != 1) {
                throw new IllegalStateException("完成纠正次数写入竞争");
            }
            String commandId = UUID.randomUUID().toString();
            String payloadJson = RuntimePayloads.toJson(objectMapper, Map.of("message", CORRECTION_MESSAGE));
            if (commandMapper.insertControl(commandId, row.userId(),
                    "task.completion-correction:" + row.taskId(), event.turnId(),
                    ContentDigests.sha256(payloadJson), row.taskId(), target.bindingId(),
                    RuntimeCommandAction.SUBMIT.name(), payloadJson,
                    LocalDateTime.now(ZoneOffset.UTC)) != 1) {
                throw new IllegalStateException("完成纠正命令写入失败");
            }
            log.info("完成候选缺失，已排入一次纠正 taskId={} bindingId={} turnId={} commandId={}",
                    row.taskId(), target.bindingId(), event.turnId(), commandId);
            return true;
        }
        if (row.correctionCount() != 1) {
            throw new IllegalStateException("完成纠正次数超出约束");
        }
        if (completionMapper.failWithoutProposal(row.userId(), row.taskId()) != 1) {
            throw new IllegalStateException("无完成候选的任务终态写入竞争");
        }
        if (conversationMapper.clearActiveTask(row.userId(), row.conversationId(), row.taskId()) != 1) {
            throw new IllegalStateException("对话目标占用与失败终态不一致");
        }
        String producerPrefix = "completion:" + row.taskId() + ":" + event.turnId();
        publish(row, producerPrefix + ":answer.completed", "answer.completed",
                Map.of("text", MISSING_PROPOSAL_ANSWER, "evidenceRefs", List.of()));
        publish(row, producerPrefix + ":task.completed", "task.completed",
                Map.of("status", GoalTaskStatus.FAILED.name(), "version", row.version() + 1));
        log.warn("纠正后仍无完成候选，目标已失败 taskId={} bindingId={} turnId={}",
                row.taskId(), target.bindingId(), event.turnId());
        return true;
    }

    /**
     * 原生回合未成功且用户未取消时阻塞原任务，清除该失败回合的完成候选。
     * 原因只记录平台归一化文案，避免把模型或框架异常原文暴露给页面。
     */
    @Transactional
    public boolean blockAfterNativeRun(ProjectionTarget target, NativeEventView event,
                                       NativeRunOutcome outcome) {
        FinalizationRow row = completionMapper.lockTask(target.userId(), target.taskId());
        if (row == null || row.cancellationRequested()
                || !GoalTaskStatus.RUNNING.name().equals(row.status())
                && !GoalTaskStatus.QUEUED.name().equals(row.status())) {
            return false;
        }
        String reason = switch (outcome) {
            case FAILED -> "原生回合执行失败，需核对后恢复";
            case SUSPENDED -> "原生回合已挂起，当前版本无法继续此类交互";
            case INTERRUPTED -> "原生回合意外中断，需核对后恢复";
            case UNKNOWN -> "原生回合状态未知，需核对后恢复";
            case COMPLETED -> throw new IllegalArgumentException("成功回合不得进入恢复阻塞");
        };
        if (completionMapper.blockForRecovery(row.userId(), row.taskId(), reason) != 1) {
            throw new IllegalStateException("恢复阻塞状态写入竞争");
        }
        publish(row, "recovery:" + target.bindingId() + ":" + event.eventId(),
                "task.recovery_blocked", Map.of("reason", reason, "nativeOutcome", outcome.code()));
        log.warn("原生回合未成功，目标等待恢复核对 taskId={} bindingId={} turnId={} outcome={}",
                row.taskId(), target.bindingId(), event.turnId(), outcome.code());
        return true;
    }

    /**
     * 停止投递时发现原生会话已无活跃运行，就地取消目标并释放会话占用。
     */
    @Transactional
    public void finalizeCancelledTask(String userId, String taskId) {
        finalizeCancelled(userId, taskId, "stop-delivery:" + taskId + ":task.completed");
    }

    private boolean finalizeCancelled(String userId, String taskId, String producerKey) {
        FinalizationRow row = completionMapper.lockTask(userId, taskId);
        if (row == null || !row.cancellationRequested()
                || !GoalTaskStatus.RUNNING.name().equals(row.status())
                && !GoalTaskStatus.CANCELLING.name().equals(row.status())) {
            return false;
        }
        if (completionMapper.cancelTask(row.userId(), row.taskId()) != 1) {
            return false;
        }
        if (conversationMapper.clearActiveTask(row.userId(), row.conversationId(), row.taskId()) != 1) {
            throw new IllegalStateException("对话目标占用与取消终态不一致");
        }
        publish(row, producerKey, "task.completed",
                Map.of("status", GoalTaskStatus.CANCELLED.name(), "version", row.version() + 1));
        log.info("目标已随停止意图取消 taskId={} userId={}", row.taskId(), userId);
        return true;
    }

    private void publish(FinalizationRow row, String producerKey, String type, Object payload) {
        if (projectionMapper.reserveEventSequence(row.userId(), row.taskId()) != 1) {
            throw new IllegalStateException("最终事件序号保留失败");
        }
        Long sequence = projectionMapper.eventSequence(row.userId(), row.taskId());
        if (sequence == null) {
            throw new IllegalStateException("最终事件序号读取失败");
        }
        taskEventMapper.insertProjected(row.userId(), row.taskId(), sequence,
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
