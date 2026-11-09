package com.agentsquad.squad.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.model.ActivityBusinessFact;
import com.agentsquad.squad.domain.response.MessageReceiptResponse;
import com.agentsquad.squad.domain.enums.ResponseExecutionStatus;
import com.agentsquad.squad.mapper.BindingMapper;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.mapper.ResponseRuntimeMapper;
import com.agentsquad.squad.mapper.ResponseContextMapper;
import com.agentsquad.squad.mapper.MessageAcceptanceMapper;
import com.agentsquad.squad.domain.enums.ResponseCompletionProtocol;
import com.agentsquad.squad.mapper.ExecutionFailureMapper;
import com.agentsquad.squad.domain.model.MasterAgentIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * 失败回答的显式后继执行受理服务。
 * <p>
 * 保留失败终态及来源消息，继承原预算账户；只允许服务端认可且无开放效果的重试，不重建用户发言。
 */
@Service
public class ResponseRetryService {
    @Resource
    private com.agentsquad.squad.common.observability.ExecutionTrace executionTrace;
    @Resource
    private com.agentsquad.squad.service.ActivityProjectionService activities;
    @org.springframework.beans.factory.annotation.Value("${agentsquad.response.completion-protocol:NATURAL}")
    private ResponseCompletionProtocol completionProtocol;

    @Resource
    private FinalDeliveryService finalDeliveries;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ResponseRuntimeMapper responses;

    @Resource
    private ExecutionFailureMapper failures;

    @Resource
    private MessageAcceptanceMapper messages;

    @Resource
    private BindingMapper bindings;

    @Resource
    private CommandMapper commands;

    @Resource
    private ResponseContextMapper material;

    @Resource
    private ArtifactService artifacts;

    @Resource
    private ConversationEventWriterService events;

    @Resource
    private ObjectMapper json;

    /**
     * 同键返回原后继收据；多个键不能为同一失败记录建立两个后继，UNKNOWN账本不允许重试。
     */
    @Transactional
    public MessageReceiptResponse retry(String userId, String executionId, String key, long expectedVersion) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "重试幂等键无效");
        }
        String oldBinding = responses.bindingForExecution(userId, executionId);
        var scope = scopes.lockOwned(userId, scopeMapper.findScopeByBinding(userId, oldBinding), false);
        scopes.requireReadable(userId, scope.getScopeId());
        var original = responses.lockByBinding(userId, oldBinding);
        String scopeKey = "response.retry:" + executionId;
        String hash = ContentDigests.sha256(RuntimePayloads.toJson(json, Map.of("expectedVersion", expectedVersion)));
        var prior = messages.findSubmission(userId, scopeKey, key);
        if (prior != null) {
            if (!hash.equals(prior.getPayloadHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "同一重试键的请求不同");
            }
            return new MessageReceiptResponse(prior.getConversationId(), prior.getMessageId(), prior.getExecutionId(), prior.getCommandId(), ResponseExecutionStatus.valueOf(prior.getStatus()), prior.getConversationId() + ":" + prior.getGeneration() + ":" + prior.getReceiptSequence());
        }
        var failure = failures.latest(userId, scope.getScopeId(), oldBinding);
        if (original.getVersion() != expectedVersion || failure == null
                || !failures.retryAllowed(userId, executionId)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "当前状态不允许重试，请刷新对话");
        }
        String next = UUID.randomUUID().toString();
        String nextScope = UUID.randomUUID().toString();
        String binding = UUID.randomUUID().toString();
        String command = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        scopeMapper.insertScope(nextScope, userId, scope.getConversationId(), scope.getConversationGeneration(), "RESPONSE", next, scope.getAccountId());
        executionTrace.capture(userId, nextScope);
        scopeMapper.inheritTrace(userId, nextScope, scope.getScopeId());
        bindings.insertResponse(binding, userId, nextScope, MasterAgentIdentity.AGENT_ID, UUID.randomUUID().toString(), now);
        messages.insertExecution(next, userId, scope.getConversationId(), original.getSourceMessageId(), nextScope, binding, completionProtocol);
        var previousModel = responses.modelChoiceByBinding(userId, oldBinding);
        if (previousModel != null) {
            messages.setModelChoice(userId, next, previousModel);
        }
        messages.reserveMessage(userId, scope.getConversationId(), now);
        finalDeliveries.retry(userId, original.getSourceMessageId(), executionId, next);
        failures.linkRetry(userId, next, executionId);
        artifacts.attachScope(userId, nextScope, material.artifactIds(userId, original.getSourceMessageId()));
        String payload = RuntimePayloads.toJson(json, Map.of("message", "重新回答原用户消息，保留已有事实。", "sourceMessageId", original.getSourceMessageId(), "retryOfExecutionId", executionId));
        commands.insertControl(command, userId, scopeKey, key, hash, null, binding, "MESSAGE_SUBMIT", payload, now);
        var created = scopeMapper.findOwned(userId, nextScope);
        events.publish(created, "command:" + command + ":execution.queued", "execution.queued", Map.of("executionId", next, "sourceMessageId", original.getSourceMessageId(), "retryOfExecutionId", executionId, "status", "QUEUED"));
        activities.completed(created, binding, new com.agentsquad.squad.domain.model.ActivityBusinessFact(
                com.agentsquad.squad.common.enums.execution.ActivityType.USER_RETRY_SCHEDULED, command));
        return new MessageReceiptResponse(scope.getConversationId(), original.getSourceMessageId(), next, command, ResponseExecutionStatus.QUEUED, scope.getConversationId() + ":" + scope.getConversationGeneration() + ":" + messages.eventSequence(userId, scope.getConversationId()));
    }
}
