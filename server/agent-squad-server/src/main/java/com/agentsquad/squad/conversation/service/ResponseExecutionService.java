package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.runtime.service.ExecutionFailureService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.domain.ResponseRuntimeRow;
import com.agentsquad.squad.conversation.domain.ResponseCompletionProtocol;
import com.agentsquad.squad.conversation.domain.NaturalReplyEvidence;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper;
import com.agentsquad.squad.integration.agentscope.NativeEventView;
import com.agentsquad.squad.integration.agentscope.NativeSubmissionReceipt;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.presentation.dto.CompletionProposalReceipt;
import com.agentsquad.squad.presentation.dto.MessageCompletedPayload;
import com.agentsquad.squad.presentation.dto.ExecutionChangedPayload;
import com.agentsquad.squad.presentation.domain.ConversationDeltaType;
import com.agentsquad.squad.presentation.repository.ConversationReadMapper;
import com.agentsquad.squad.conversation.dto.MessageView;
import com.agentsquad.squad.conversation.dto.ResponseExecutionView;
import com.agentsquad.squad.conversation.domain.ResponseExecutionStatus;
import com.agentsquad.squad.runtime.domain.NativeRunOutcome;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
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
    private ConversationEventWriter events;

    @Resource
    private ConversationReadMapper conversationReads;

    @Resource
    private ObjectMapper json;

    /**
     * 只允许当前有权限的 Agent 会话提交答案；同一回合重复提交相同内容时返回原结果，执行真正成功前不向用户发布。
     */
    @Transactional
    public CompletionProposalReceipt propose(String userId, String bindingId, String turnId, String callId,
                                             String outcome, String answer) {
        if (turnId == null || turnId.isBlank() || callId == null || callId.isBlank()
                || !Set.of("SUCCEEDED", "PARTIAL").contains(outcome) || answer == null || answer.isBlank()
                || answer.length() > 200000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "回复候选内容或运行身份无效");
        }
        ExecutionScopeRow scope = lock(userId, bindingId, true);
        ResponseRuntimeRow row = replies.lockByBinding(userId, bindingId);
        if (!"RESPONSE".equals(scope.kind()) || row.cancellationRequested() || TERMINAL.contains(row.status())
                || row.completionProtocol() == ResponseCompletionProtocol.NATURAL) {
            throw conflict("回复已不能提交完成候选");
        }
        if (replies.uncertain(userId, scope.scopeId()) > 0 || replies.openInteractions(userId, scope.scopeId()) > 0) {
            throw conflict("仍有待处理交互或未知操作，不能结束回复");
        }
        String value = RuntimePayloads.toJson(json, Map.of("outcome", outcome, "finalAnswer", answer,
                "sourceBindingId", bindingId, "sourceTurnId", turnId, "toolCallId", callId));
        // 工具调用ID可变化，业务候选身份仅由Turn、结果和回答决定。
        String hash = ContentDigests.sha256(RuntimePayloads.toJson(json, Map.of("outcome", outcome, "finalAnswer", answer)));
        if (row.proposalJson() != null) {
            if (turnId.equals(row.proposalTurnId()) && hash.equals(row.proposalHash())) {
                return new CompletionProposalReceipt(null, bindingId, turnId, read(row.proposalJson()).path("toolCallId").asText(), "RECORDED");
            }
            throw conflict("回复已有不同的完成候选");
        }
        if (replies.propose(userId, bindingId, turnId, value, hash) != 1) {
            throw conflict("回复候选提交时状态已变化");
        }
        log.info("回复完成候选已保存 executionId={} bindingId={} turnId={} outcome={} answerLength={}",
                row.executionId(), bindingId, turnId, outcome, answer.length());
        return new CompletionProposalReceipt(null, bindingId, turnId, callId, "RECORDED");
    }

    /**
     * 更新回复状态、纠正命令、助手消息时，同时保存本次处理到的 Agent 执行事件序号；失败则一起回滚，已处理的事件不再重复执行。
     */
    @Transactional
    public void accept(ExecutionScopeRow scope, ProjectionTarget target, NativeEventView event) {
        // 与目标创建保持会话→来源回复→交付位的锁序，避免完成与转交相互等待。
        scopes.lockOwned(scope.userId(), scope.scopeId(), false);
        ResponseRuntimeRow row = replies.lockByBinding(scope.userId(), target.bindingId());
        if (row == null || TERMINAL.contains(row.status())) {
            return;
        }
        ConversationRow conversation = conversations.lockOwned(scope.userId(), scope.conversationId());
        if ((!"ACTIVE".equals(conversation.status()) || conversation.generation() != scope.conversationGeneration())
                && !row.cancellationRequested()) {
            replies.cancelling(scope.userId(), row.bindingId(), now());
            row = replies.lockByBinding(scope.userId(), row.bindingId());
        }
        if ("run/start".equals(event.type())) {
            if (!row.cancellationRequested()) {
                state(scope, row, "RUNNING", "native:" + event.eventId());
            }
            return;
        }
        if (!"run/end".equals(event.type())) {
            return;
        }
        if (row.cancellationRequested()) {
            settleCancelled(scope, row);
            return;
        }
        if (replies.uncertain(scope.userId(), scope.scopeId()) > 0) {
            state(scope, row, "RECONCILING", "native:" + event.eventId());
            return;
        }
        NativeRunOutcome outcome = NativeRunOutcome.fromCode(event.outcome());
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
            failures.ensureNativeFailure(scope, row.bindingId(), "native:" + event.eventId());
            if (outcome == NativeRunOutcome.FAILED && failures.queueTransientRetry(scope, row.bindingId(), event.eventId(), event.turnId())) {
                return;
            }
            finish(scope, row, "FAILED", "本次回答暂未完成，请稍后重试。");
            return;
        }
        if (row.completionProtocol() == ResponseCompletionProtocol.NATURAL) {
            if (replies.openInteractions(scope.userId(), scope.scopeId()) > 0) {
                state(scope, row, waiting(scope), "native:" + event.eventId());
                return;
            }
            if (!(event.data().get("naturalOutput") instanceof NaturalReplyEvidence output)) {
                throw new IllegalStateException("自然回复缺少已提交输出证据");
            }
            String text = output.text();
            if (output.notice() != null) {
                text = text == null ? output.notice() : text + "\n\n" + output.notice();
            }
            finish(scope, row, output.status().name(), text);
            return;
        }
        JsonNode proposal = row.proposalJson() == null ? null : read(row.proposalJson());
        if (validProposal(row, target, event, proposal)) {
            if (replies.uncertain(scope.userId(), scope.scopeId()) > 0) {
                state(scope, row, "RECONCILING", "native:" + event.eventId());
            } else if (replies.openInteractions(scope.userId(), scope.scopeId()) > 0) {
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
    public void recover(ProjectionTarget target, NativeSubmissionReceipt receipt) {
        recover(target, receipt, null);
    }

    /** 核对结束后仅使用持久原生日志恢复自然答案；缺少证据保持核对，不转入旧候选纠正回合。 */
    @Transactional
    public void recover(ProjectionTarget target, NativeSubmissionReceipt receipt, NaturalReplyEvidence evidence) {
        ExecutionScopeRow scope = lock(target.userId(), target.bindingId(), false);
        ResponseRuntimeRow row = replies.lockByBinding(target.userId(), target.bindingId());
        if (row == null || TERMINAL.contains(row.status())) {
            return;
        }
        if (row.cancellationRequested()) {
            if (Set.of("completed", "failed", "interrupted").contains(receipt.status())) {
                settleCancelled(scope, row);
            }
            return;
        }
        if ("completed".equals(receipt.status()) && "RECONCILING".equals(row.status())
                && replies.uncertain(scope.userId(), scope.scopeId()) == 0
                && replies.openInteractions(scope.userId(), scope.scopeId()) == 0) {
            if (row.completionProtocol() == ResponseCompletionProtocol.NATURAL) {
                if (evidence != null) {
                    String text = evidence.text();
                    if (evidence.notice() != null) {
                        text = text == null ? evidence.notice() : text + "\n\n" + evidence.notice();
                    }
                    finish(scope, row, evidence.status().name(), text);
                }
                return;
            }
            JsonNode proposal = row.proposalJson() == null ? null : read(row.proposalJson());
            NativeEventView completed = new NativeEventView(0, "receipt", "run/end", receipt.runId(), receipt.turnId(),
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
        scopes.requireReadable(scope.userId(), scope.scopeId());
        if (replies.uncertain(scope.userId(), scope.scopeId()) > 0) {
            state(scope, row, "RECONCILING", "recovery:unknown:" + receipt.turnId() + ":" + receipt.runId());
            return;
        }
        String key = receipt.turnId() + ":" + receipt.runId();
        if (commands.findByKeyCurrent(scope.userId(), "response.resume:" + row.executionId(), key) != null
                || commands.countPendingWorkerResumes(scope.userId(), row.bindingId()) > 0) {
            return;
        }
        if (row.recoveryAttempts() >= 2) {
            finish(scope, row, "FAILED", "回答恢复未成功，已保留你的消息，请稍后重试。");
            return;
        }
        replies.recovering(scope.userId(), row.bindingId());
        command(scope, row, "response.resume:" + row.executionId(), key, "RESUME", Map.of("turnId", receipt.turnId()));
        publishState(scope, row, "RECOVERING", "recovery:" + key);
        log.info("回复原Turn恢复已排队 executionId={} turnId={} attempt={}", row.executionId(), receipt.turnId(), row.recoveryAttempts() + 1);
    }

    @Transactional
    public void stopped(String userId, String bindingId) {
        ExecutionScopeRow scope = lock(userId, bindingId, false);
        settleCancelled(scope, replies.lockByBinding(userId, bindingId));
    }

    @Transactional
    public void rejectedResume(String userId, String bindingId) {
        ExecutionScopeRow scope = lock(userId, bindingId, false);
        ResponseRuntimeRow row = replies.lockByBinding(userId, bindingId);
        if (row != null && !TERMINAL.contains(row.status())) {
            if (row.cancellationRequested()) {
                settleCancelled(scope, row);
            } else {
                finish(scope, row, "FAILED", "回答恢复未成功，已保留你的消息，请稍后重试。");
            }
        }
    }

    public String originalCommand(ProjectionTarget target) {
        return replies.originalCommand(target.userId(), target.bindingId());
    }

    private String waiting(ExecutionScopeRow scope) {
        var kinds = replies.openKinds(scope.userId(), scope.scopeId());
        if (kinds.contains("APPROVAL")) {
            return "WAITING_APPROVAL";
        }
        return kinds.contains("INPUT") ? "WAITING_INPUT" : null;
    }

    private boolean validProposal(ResponseRuntimeRow row, ProjectionTarget target, NativeEventView event, JsonNode proposal) {
        return proposal != null && event.turnId() != null && event.turnId().equals(row.proposalTurnId())
                && target.bindingId().equals(proposal.path("sourceBindingId").asText())
                && event.turnId().equals(proposal.path("sourceTurnId").asText())
                && Set.of("SUCCEEDED", "PARTIAL").contains(proposal.path("outcome").asText())
                && !proposal.path("finalAnswer").asText().isBlank()
                && ContentDigests.sha256(RuntimePayloads.toJson(json, Map.of("outcome", proposal.path("outcome").asText(),
                "finalAnswer", proposal.path("finalAnswer").asText()))).equals(row.proposalHash());
    }

    private void correctOrFail(ExecutionScopeRow scope, ResponseRuntimeRow row, NativeEventView event) {
        if (row.correctionCount() == 0) {
            if (replies.correct(scope.userId(), row.bindingId()) != 1) {
                throw new IllegalStateException("回复纠正写入竞争");
            }
            command(scope, row, "response.correction:" + row.executionId(), event.turnId(), "SUBMIT",
                    Map.of("message", "请核对当前用户消息，调用 propose_completion 提交完整回答候选；无法完整完成时选择 PARTIAL 并说明缺口。"));
            publishState(scope, row, "QUEUED", "correction:" + event.eventId());
        } else {
            failures.record(scope, row.bindingId(), "completion-correction-exhausted",
                    new com.agentsquad.squad.runtime.domain.FailureClassification("LOGIC_INVALID", "COMPLETION_INVALID", "SYSTEM", "本次未能形成有效回答，已保留你的消息。"));
            finish(scope, row, "FAILED", "本次未能生成有效回答，已保留你的消息，请稍后重试。");
        }
    }

    private void settleCancelled(ExecutionScopeRow scope, ResponseRuntimeRow row) {
        if (row == null || !row.cancellationRequested() || TERMINAL.contains(row.status())) {
            return;
        }
        if (replies.uncertain(scope.userId(), scope.scopeId()) > 0) {
            if (!"RECONCILING".equals(row.status())) {
                state(scope, row, "RECONCILING", "cancel:unknown");
            }
        } else {
            finish(scope, row, "CANCELLED", null);
        }
    }

    private void finish(ExecutionScopeRow scope, ResponseRuntimeRow row, String status, String answer) {
        if ("SUCCEEDED".equals(status) || "PARTIAL".equals(status) || "CANCELLED".equals(status)) {
            failures.resolve(scope.userId(), row.bindingId());
        }
        if ("FAILED".equals(status)) {
            failures.ensureNativeFailure(scope, row.bindingId(), "execution-failed:" + row.executionId());
        }
        String answerId = null;
        ConversationRow conversation = conversations.lockOwned(scope.userId(), scope.conversationId());
        if (answer != null && "ACTIVE".equals(conversation.status())
                && conversation.generation() == scope.conversationGeneration()) {
            answerId = finalDeliveries.claimPublication(scope.userId(), row.sourceMessageId(), "RESPONSE", row.executionId());
        }
        if (answerId != null) {
            messages.reserveMessage(scope.userId(), scope.conversationId(), now());
            long answerSequence = messages.messageSequence(scope.userId(), scope.conversationId());
            LocalDateTime answerTime = now();
            replies.answer(answerId, scope.userId(), scope.conversationId(), answerSequence,
                    row.sourceMessageId(), row.executionId(), answer, answerTime);
            var message = new MessageView(answerId, answerSequence, "ASSISTANT", answer, 0, true,
                    row.sourceMessageId(), row.executionId(), null, answerTime.atOffset(ZoneOffset.UTC).toString(), java.util.List.of());
            events.publish(scope, "response:" + row.executionId() + ":answer", ConversationDeltaType.MESSAGE_COMPLETED.code(),
                    new MessageCompletedPayload(ConversationDeltaType.SCHEMA_VERSION, message));
        }
        if ("CANCELLED".equals(status)) {
            finalDeliveries.close(scope.userId(), row.sourceMessageId(), "RESPONSE", row.executionId());
        }
        if (replies.finish(scope.userId(), row.bindingId(), status, answerId) != 1) {
            throw new IllegalStateException("回复终态写入竞争");
        }
        publishState(scope, row, status, "finished");
        log.info("回复已终结 executionId={} bindingId={} status={}", row.executionId(), row.bindingId(), status);
    }

    private void state(ExecutionScopeRow scope, ResponseRuntimeRow row, String status, String producer) {
        if (!status.equals(row.status())) {
            replies.status(scope.userId(), row.bindingId(), status);
            publishState(scope, row, status, producer);
        }
    }

    private void publishState(ExecutionScopeRow scope, ResponseRuntimeRow row, String status, String producer) {
        var current = conversationReads.execution(scope.userId(), scope.conversationId(), row.executionId());
        if (current == null) {
            return;
        }
        boolean terminal = TERMINAL.contains(current.status());
        var allowed = "FAILED".equals(current.status()) && failures.canRetry(scope.userId(), current.executionId())
                ? java.util.List.of("RETRY") : current.cancellationRequested() || terminal || "CANCELLING".equals(current.status())
                ? java.util.List.<String>of() : java.util.List.of("STOP");
        var execution = new ResponseExecutionView(current.executionId(), current.sourceMessageId(),
                ResponseExecutionStatus.valueOf(current.status()), current.version(), current.answerMessageId(), current.failureId(), allowed);
        events.publish(scope, "response:" + row.executionId() + ":" + producer, ConversationDeltaType.EXECUTION_STATUS_CHANGED.code(),
                new ExecutionChangedPayload(ConversationDeltaType.SCHEMA_VERSION, execution));
    }

    private void command(ExecutionScopeRow scope, ResponseRuntimeRow row, String scopeKey, String key, String action, Object payload) {
        String value = RuntimePayloads.toJson(json, payload);
        commands.insertControl(UUID.randomUUID().toString(), scope.userId(), scopeKey, key, ContentDigests.sha256(value),
                null, row.bindingId(), action, value, now());
    }

    private ExecutionScopeRow lock(String userId, String bindingId, boolean dispatch) {
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
