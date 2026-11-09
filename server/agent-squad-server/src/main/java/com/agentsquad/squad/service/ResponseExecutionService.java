package com.agentsquad.squad.service;

import com.agentsquad.squad.enums.*;
import com.agentsquad.squad.po.ConversationPO;
import com.agentsquad.squad.po.ExecutionScopePO;
import com.agentsquad.squad.po.ResponseRuntimePO;
import com.agentsquad.squad.bo.FailureClassificationBO;

import com.agentsquad.squad.bo.NaturalReplyEvidenceBO;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.mapper.MessageAcceptanceMapper;
import com.agentsquad.squad.mapper.ResponseRuntimeMapper;
import com.agentsquad.squad.bo.NativeEventViewBO;
import com.agentsquad.squad.bo.NativeSubmissionReceiptBO;
import com.agentsquad.squad.bo.CompletionProposalReceiptBO;
import com.agentsquad.squad.bo.MessageCompletedPayloadBO;
import com.agentsquad.squad.bo.ExecutionChangedPayloadBO;
import com.agentsquad.squad.mapper.ConversationReadMapper;
import com.agentsquad.squad.vo.MessageVO;
import com.agentsquad.squad.vo.ResponseExecutionVO;
import com.agentsquad.squad.bo.ProjectionTargetBO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 负责管理一次普通回复的开始、等待、恢复、完成和取消状态。
 * <p>
 * 普通回复有独立的消息来源、Agent 会话和预算。只有同一次 Agent 回合成功且答案有效时才发布；取消优先，外部操作结果不明时不能当作已安全结束。
 */
@Service
@Slf4j
public class ResponseExecutionService {
    @Resource
    private com.agentsquad.squad.mapper.GoalCreationMapper goalOrigins;
    @Resource
    private com.agentsquad.squad.service.ActivityProjectionService activities;
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED");

    @Resource
    private FinalDeliveryService finalDeliveries;

    @Resource
    private ExecutionFailureService failures;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ResponseRuntimeMapper replies;

    @Resource
    private MessageAcceptanceMapper messages;

    @Resource
    private ConversationMapper conversations;

    @Resource
    private CommandMapper commands;

    @Resource
    private ConversationEventWriterService events;

    @Resource
    private ConversationReadMapper conversationReads;

    @Resource
    private ObjectMapper json;

    /**
     * 只允许当前有权限的 Agent 会话提交答案；同一回合重复提交相同内容时返回原结果，执行真正成功前不向用户发布。
     */
    @Transactional
    public CompletionProposalReceiptBO propose(String userId, String bindingId, String turnId, String callId,
                                             String outcome, String answer) {
        if (turnId == null || turnId.isBlank() || callId == null || callId.isBlank()
                || !Set.of("SUCCEEDED", "PARTIAL").contains(outcome) || answer == null || answer.isBlank()
                || answer.length() > 200000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "回复候选内容或运行身份无效");
        }
        ExecutionScopePO scope = lock(userId, bindingId, true);
        ResponseRuntimePO row = replies.lockByBinding(userId, bindingId);
        if (!"RESPONSE".equals(scope.getKind()) || row.isCancellationRequested() || TERMINAL.contains(row.getStatus())
                || row.getCompletionProtocol() == ResponseCompletionProtocol.NATURAL) {
            throw conflict("回复已不能提交完成候选");
        }
        if (replies.uncertain(userId, scope.getScopeId()) > 0 || replies.openInteractions(userId, scope.getScopeId()) > 0) {
            throw conflict("仍有待处理交互或未知操作，不能结束回复");
        }
        String value = RuntimePayloads.toJson(json, Map.of("outcome", outcome, "finalAnswer", answer,
                "sourceBindingId", bindingId, "sourceTurnId", turnId, "toolCallId", callId));
        // 工具调用ID可变化，业务候选身份仅由Turn、结果和回答决定。
        String hash = ContentDigests.sha256(RuntimePayloads.toJson(json, Map.of("outcome", outcome, "finalAnswer", answer)));
        if (row.getProposalJson() != null) {
            if (turnId.equals(row.getProposalTurnId()) && hash.equals(row.getProposalHash())) {
                return new CompletionProposalReceiptBO(null, bindingId, turnId, read(row.getProposalJson()).path("toolCallId").asText(), "RECORDED");
            }
            throw conflict("回复已有不同的完成候选");
        }
        if (replies.propose(userId, bindingId, turnId, value, hash) != 1) {
            throw conflict("回复候选提交时状态已变化");
        }
        log.info("回复完成候选已保存 executionId={} bindingId={} turnId={} outcome={} answerLength={}",
                row.getExecutionId(), bindingId, turnId, outcome, answer.length());
        return new CompletionProposalReceiptBO(null, bindingId, turnId, callId, "RECORDED");
    }

    /**
     * 更新回复状态、纠正命令、助手消息时，同时保存本次处理到的 Agent 执行事件序号；失败则一起回滚，已处理的事件不再重复执行。
     */
    @Transactional
    public void accept(ExecutionScopePO scope, ProjectionTargetBO target, NativeEventViewBO event) {
        // 与目标创建保持会话→来源回复→交付位的锁序，避免完成与转交相互等待。
        scopes.lockOwned(scope.getUserId(), scope.getScopeId(), false);
        ResponseRuntimePO row = replies.lockByBinding(scope.getUserId(), target.getBindingId());
        if (row == null || TERMINAL.contains(row.getStatus())) {
            return;
        }
        ConversationPO conversation = conversations.lockOwned(scope.getUserId(), scope.getConversationId());
        if ((!"ACTIVE".equals(conversation.getStatus()) || conversation.getGeneration() != scope.getConversationGeneration())
                && !row.isCancellationRequested()) {
            replies.cancelling(scope.getUserId(), row.getBindingId(), now());
            row = replies.lockByBinding(scope.getUserId(), row.getBindingId());
        }
        if ("run/start".equals(event.type())) {
            if (!row.isCancellationRequested()) {
                state(scope, row, "RUNNING", "native:" + event.eventId());
            }
            return;
        }
        if (!"run/end".equals(event.type())) {
            return;
        }
        if (row.isCancellationRequested()) {
            settleCancelled(scope, row);
            return;
        }
        if (replies.uncertain(scope.getUserId(), scope.getScopeId()) > 0) {
            state(scope, row, "RECONCILING", "native:" + event.eventId());
            return;
        }
        NativeRunOutcome outcome = NativeRunOutcome.fromCode(event.outcome());
        // 目标已持久受理，来源回复只结算交接，正式答案由目标发布。
        if (outcome == NativeRunOutcome.COMPLETED
                && replies.openInteractions(scope.getUserId(), scope.getScopeId()) == 0
                && goalOrigins.taskForSourceExecution(scope.getUserId(), row.getExecutionId()) != null) {
            finish(scope, row, "SUCCEEDED", null);
            return;
        }
        if (outcome == NativeRunOutcome.SUSPENDED) {
            String waiting = waiting(scope);
            if (waiting != null) {
                state(scope, row, waiting, "native:" + event.eventId());
                return;
            }
        }
        if (outcome == NativeRunOutcome.INTERRUPTED) {
            state(scope, row, "RECOVERING", "native:" + event.eventId());
            return;
        }
        if (outcome != NativeRunOutcome.COMPLETED) {
            failures.ensureNativeFailure(scope, row.getBindingId(), "native:" + event.eventId());
            if (outcome == NativeRunOutcome.FAILED && failures.queueTransientRetry(scope, row.getBindingId(), event.eventId(), event.turnId())) {
                return;
            }
            finish(scope, row, "FAILED", "本次回答暂未完成，请稍后重试。");
            return;
        }
        if (row.getCompletionProtocol() == ResponseCompletionProtocol.NATURAL) {
            if (replies.openInteractions(scope.getUserId(), scope.getScopeId()) > 0) {
                state(scope, row, waiting(scope), "native:" + event.eventId());
                return;
            }
            if (!(event.data().get("naturalOutput") instanceof NaturalReplyEvidenceBO output)) {
                throw new IllegalStateException("自然回复缺少已提交输出证据");
            }
            String text = output.text();
            if (output.notice() != null) {
                text = text == null ? output.notice() : text + "\n\n" + output.notice();
            }
            finish(scope, row, output.status().name(), text);
            return;
        }
        JsonNode proposal = row.getProposalJson() == null ? null : read(row.getProposalJson());
        if (validProposal(row, target, event, proposal)) {
            if (replies.uncertain(scope.getUserId(), scope.getScopeId()) > 0) {
                state(scope, row, "RECONCILING", "native:" + event.eventId());
            } else if (replies.openInteractions(scope.getUserId(), scope.getScopeId()) > 0) {
                String waiting = waiting(scope);
                state(scope, row, waiting, "native:" + event.eventId());
            } else {
                finish(scope, row, proposal.path("outcome").asText(), proposal.path("finalAnswer").asText());
            }
        } else {
            correctOrFail(scope, row, event);
        }
    }

    /**
     * AgentScope 确认原回合中断后安排恢复；同一次运行只安排一次恢复，不重新提交用户消息。
     */
    @Transactional
    public void recover(ProjectionTargetBO target, NativeSubmissionReceiptBO receipt) {
        recover(target, receipt, null);
    }

    /** 核对结束后仅使用持久原生日志恢复自然答案；缺少证据保持核对，不转入旧候选纠正回合。 */
    @Transactional
    public void recover(ProjectionTargetBO target, NativeSubmissionReceiptBO receipt, NaturalReplyEvidenceBO evidence) {
        ExecutionScopePO scope = lock(target.getUserId(), target.getBindingId(), false);
        ResponseRuntimePO row = replies.lockByBinding(target.getUserId(), target.getBindingId());
        if (row == null || TERMINAL.contains(row.getStatus())) {
            return;
        }
        if (row.isCancellationRequested()) {
            if (Set.of("completed", "failed", "interrupted").contains(receipt.status())) {
                settleCancelled(scope, row);
            }
            return;
        }
        if ("completed".equals(receipt.status()) && "RECONCILING".equals(row.getStatus())
                && replies.uncertain(scope.getUserId(), scope.getScopeId()) == 0
                && replies.openInteractions(scope.getUserId(), scope.getScopeId()) == 0) {
            if (goalOrigins.taskForSourceExecution(scope.getUserId(), row.getExecutionId()) != null) {
                finish(scope, row, "SUCCEEDED", null);
                return;
            }
            if (row.getCompletionProtocol() == ResponseCompletionProtocol.NATURAL) {
                if (evidence != null) {
                    String text = evidence.text();
                    if (evidence.notice() != null) {
                        text = text == null ? evidence.notice() : text + "\n\n" + evidence.notice();
                    }
                    finish(scope, row, evidence.status().name(), text);
                }
                return;
            }
            JsonNode proposal = row.getProposalJson() == null ? null : read(row.getProposalJson());
            NativeEventViewBO completed = new NativeEventViewBO(0, "receipt", "run/end", receipt.runId(), receipt.turnId(),
                    "completed", 0, Map.of());
            if (validProposal(row, target, completed, proposal)) {
                finish(scope, row, proposal.path("outcome").asText(), proposal.path("finalAnswer").asText());
            } else {
                correctOrFail(scope, row, completed);
            }
            return;
        }
        if (!"interrupted".equals(receipt.status()) || receipt.turnId() == null) {
            return;
        }
        scopes.requireReadable(scope.getUserId(), scope.getScopeId());
        if (replies.uncertain(scope.getUserId(), scope.getScopeId()) > 0) {
            state(scope, row, "RECONCILING", "recovery:unknown:" + receipt.turnId() + ":" + receipt.runId());
            return;
        }
        String key = receipt.turnId() + ":" + receipt.runId();
        if (commands.findByKeyCurrent(scope.getUserId(), "response.resume:" + row.getExecutionId(), key) != null
                || commands.countPendingWorkerResumes(scope.getUserId(), row.getBindingId()) > 0) {
            return;
        }
        if (row.getRecoveryAttempts() >= 2) {
            finish(scope, row, "FAILED", "回答恢复未成功，已保留你的消息，请稍后重试。");
            return;
        }
        replies.recovering(scope.getUserId(), row.getBindingId());
        command(scope, row, "response.resume:" + row.getExecutionId(), key, "RESUME", Map.of("turnId", receipt.turnId()));
        publishState(scope, row, "RECOVERING", "recovery:" + key);
        log.info("回复原Turn恢复已排队 executionId={} turnId={} attempt={}", row.getExecutionId(), receipt.turnId(), row.getRecoveryAttempts() + 1);
    }

    @Transactional
    public void stopped(String userId, String bindingId) {
        ExecutionScopePO scope = lock(userId, bindingId, false);
        settleCancelled(scope, replies.lockByBinding(userId, bindingId));
    }

    @Transactional
    public void rejectedResume(String userId, String bindingId) {
        ExecutionScopePO scope = lock(userId, bindingId, false);
        ResponseRuntimePO row = replies.lockByBinding(userId, bindingId);
        if (row != null && !TERMINAL.contains(row.getStatus())) {
            if (row.isCancellationRequested()) {
                settleCancelled(scope, row);
            } else {
                finish(scope, row, "FAILED", "回答恢复未成功，已保留你的消息，请稍后重试。");
            }
        }
    }

    public String originalCommand(ProjectionTargetBO target) {
        return replies.originalCommand(target.getUserId(), target.getBindingId());
    }

    private String waiting(ExecutionScopePO scope) {
        var kinds = replies.openKinds(scope.getUserId(), scope.getScopeId());
        if (kinds.contains("APPROVAL")) {
            return "WAITING_APPROVAL";
        }
        return kinds.contains("INPUT") ? "WAITING_INPUT" : null;
    }

    private boolean validProposal(ResponseRuntimePO row, ProjectionTargetBO target, NativeEventViewBO event, JsonNode proposal) {
        return proposal != null && event.turnId() != null && event.turnId().equals(row.getProposalTurnId())
                && target.getBindingId().equals(proposal.path("sourceBindingId").asText())
                && event.turnId().equals(proposal.path("sourceTurnId").asText())
                && Set.of("SUCCEEDED", "PARTIAL").contains(proposal.path("outcome").asText())
                && !proposal.path("finalAnswer").asText().isBlank()
                && ContentDigests.sha256(RuntimePayloads.toJson(json, Map.of("outcome", proposal.path("outcome").asText(),
                "finalAnswer", proposal.path("finalAnswer").asText()))).equals(row.getProposalHash());
    }

    private void correctOrFail(ExecutionScopePO scope, ResponseRuntimePO row, NativeEventViewBO event) {
        if (row.getCorrectionCount() == 0) {
            if (replies.correct(scope.getUserId(), row.getBindingId()) != 1) {
                throw new IllegalStateException("回复纠正写入竞争");
            }
            command(scope, row, "response.correction:" + row.getExecutionId(), event.turnId(), "SUBMIT",
                    Map.of("message", "请核对当前用户消息，调用 propose_completion 提交完整回答候选；无法完整完成时选择 PARTIAL 并说明缺口。"));
            publishState(scope, row, "QUEUED", "correction:" + event.eventId());
        } else {
            failures.record(scope, row.getBindingId(), "completion-correction-exhausted",
                    new FailureClassificationBO("LOGIC_INVALID", "COMPLETION_INVALID", "SYSTEM", "本次未能形成有效回答，已保留你的消息。"));
            finish(scope, row, "FAILED", "本次未能生成有效回答，已保留你的消息，请稍后重试。");
        }
    }

    private void settleCancelled(ExecutionScopePO scope, ResponseRuntimePO row) {
        if (row == null || !row.isCancellationRequested() || TERMINAL.contains(row.getStatus())) {
            return;
        }
        if (replies.uncertain(scope.getUserId(), scope.getScopeId()) > 0) {
            if (!"RECONCILING".equals(row.getStatus())) {
                state(scope, row, "RECONCILING", "cancel:unknown");
            }
        } else {
            finish(scope, row, "CANCELLED", null);
        }
    }

    private void finish(ExecutionScopePO scope, ResponseRuntimePO row, String status, String answer) {
        if ("SUCCEEDED".equals(status) || "PARTIAL".equals(status)) {
            failures.resolve(scope.getUserId(), row.getBindingId());
            failures.resolveSuperseded(scope);
        } else if ("CANCELLED".equals(status)) {
            failures.resolve(scope.getUserId(), row.getBindingId());
        }
        if ("FAILED".equals(status)) {
            failures.ensureNativeFailure(scope, row.getBindingId(), "execution-failed:" + row.getExecutionId());
        }
        String answerId = null;
        ConversationPO conversation = conversations.lockOwned(scope.getUserId(), scope.getConversationId());
        if (answer != null && "ACTIVE".equals(conversation.getStatus())
                && conversation.getGeneration() == scope.getConversationGeneration()) {
            answerId = finalDeliveries.claimPublication(scope.getUserId(), row.getSourceMessageId(), "RESPONSE", row.getExecutionId());
        }
        if (answerId != null) {
            messages.reserveMessage(scope.getUserId(), scope.getConversationId(), now());
            long answerSequence = messages.messageSequence(scope.getUserId(), scope.getConversationId());
            LocalDateTime answerTime = now();
            replies.answer(answerId, scope.getUserId(), scope.getConversationId(), answerSequence,
                    row.getSourceMessageId(), row.getExecutionId(), answer, answerTime);
            var message = new MessageVO(answerId, answerSequence, "ASSISTANT", answer, 0, true,
                    row.getSourceMessageId(), row.getExecutionId(), null, answerTime.atOffset(ZoneOffset.UTC).toString(), java.util.List.of());
            events.publish(scope, "response:" + row.getExecutionId() + ":answer", ConversationDeltaType.MESSAGE_COMPLETED.code(),
                    new MessageCompletedPayloadBO(ConversationDeltaType.SCHEMA_VERSION, message));
            activities.completed(scope, new com.agentsquad.squad.bo.ActivityBusinessFactBO(
                    ActivityType.ANSWER_PUBLISH, answerId));
        }
        if ("CANCELLED".equals(status)) {
            finalDeliveries.close(scope.getUserId(), row.getSourceMessageId(), "RESPONSE", row.getExecutionId());
        }
        if (replies.finish(scope.getUserId(), row.getBindingId(), status, answerId) != 1) {
            throw new IllegalStateException("回复终态写入竞争");
        }
        publishState(scope, row, status, "finished");
        log.info("回复已终结 executionId={} bindingId={} status={}", row.getExecutionId(), row.getBindingId(), status);
    }

    private void state(ExecutionScopePO scope, ResponseRuntimePO row, String status, String producer) {
        if (!status.equals(row.getStatus())) {
            replies.status(scope.getUserId(), row.getBindingId(), status);
            publishState(scope, row, status, producer);
        }
    }

    private void publishState(ExecutionScopePO scope, ResponseRuntimePO row, String status, String producer) {
        var current = conversationReads.execution(scope.getUserId(), scope.getConversationId(), row.getExecutionId());
        if (current == null) {
            return;
        }
        boolean terminal = TERMINAL.contains(current.getStatus());
        var allowed = "FAILED".equals(current.getStatus()) && failures.canRetry(scope.getUserId(), current.getExecutionId())
                ? java.util.List.of("RETRY") : current.isCancellationRequested() || terminal || "CANCELLING".equals(current.getStatus())
                ? java.util.List.<String>of() : java.util.List.of("STOP");
        var execution = new ResponseExecutionVO(current.getExecutionId(), current.getSourceMessageId(),
                ResponseExecutionStatus.valueOf(current.getStatus()), current.getVersion(), current.getAnswerMessageId(), current.getFailureId(), allowed);
        events.publish(scope, "response:" + row.getExecutionId() + ":" + producer, ConversationDeltaType.EXECUTION_STATUS_CHANGED.code(),
                new ExecutionChangedPayloadBO(ConversationDeltaType.SCHEMA_VERSION, execution));
    }

    private void command(ExecutionScopePO scope, ResponseRuntimePO row, String scopeKey, String key, String action, Object payload) {
        String value = RuntimePayloads.toJson(json, payload);
        commands.insertControl(UUID.randomUUID().toString(), scope.getUserId(), scopeKey, key, ContentDigests.sha256(value),
                null, row.getBindingId(), action, value, now());
    }

    private ExecutionScopePO lock(String userId, String bindingId, boolean dispatch) {
        return scopes.lockOwned(userId, scopeMapper.findScopeByBinding(userId, bindingId), dispatch);
    }

    private JsonNode read(String value) {
        try {
            return RuntimePayloads.readJson(json, value);
        } catch (Exception exception) {
            throw new IllegalStateException("回复候选无法读取", exception);
        }
    }

    private RuntimeBusinessException conflict(String text) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, text);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
