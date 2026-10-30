package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.dto.MessageReceipt;
import com.agentsquad.squad.conversation.domain.ResponseExecutionStatus;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper;
import com.agentsquad.squad.conversation.repository.ResponseContextMapper;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.agentsquad.squad.runtime.repository.ExecutionFailureMapper;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.artifact.service.ArtifactService;
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
    private ConversationEventWriter events;

    @Resource
    private ObjectMapper json;

    /**
     * 同键返回原后继收据；多个键不能为同一失败记录建立两个后继，UNKNOWN账本不允许重试。
     */
    @Transactional
    public MessageReceipt retry(String userId, String executionId, String key, long expectedVersion) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "重试幂等键无效");
        }
        String oldBinding = responses.bindingForExecution(userId, executionId);
        var scope = scopes.lockOwned(userId, scopeMapper.findScopeByBinding(userId, oldBinding), false);
        scopes.requireReadable(userId, scope.scopeId());
        var original = responses.lockByBinding(userId, oldBinding);
        String scopeKey = "response.retry:" + executionId;
        String hash = ContentDigests.sha256(RuntimePayloads.toJson(json, Map.of("expectedVersion", expectedVersion)));
        var prior = messages.findSubmission(userId, scopeKey, key);
        if (prior != null) {
            if (!hash.equals(prior.payloadHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "同一重试键的请求不同");
            }
            return new MessageReceipt(prior.conversationId(), prior.messageId(), prior.executionId(), prior.commandId(), ResponseExecutionStatus.valueOf(prior.status()), prior.conversationId() + ":" + prior.generation() + ":" + prior.receiptSequence());
        }
        var failure = failures.latest(userId, scope.scopeId(), oldBinding);
        if (original.version() != expectedVersion || failure == null || !"SERVICE_TRANSIENT".equals(failure.category())
                || !failures.retryAllowed(userId, executionId)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "当前状态不允许重试，请刷新对话");
        }
        String next = UUID.randomUUID().toString();
        String nextScope = UUID.randomUUID().toString();
        String binding = UUID.randomUUID().toString();
        String command = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        scopeMapper.insertScope(nextScope, userId, scope.conversationId(), scope.conversationGeneration(), "RESPONSE", next, scope.accountId());
        bindings.insertResponse(binding, userId, nextScope, MasterAgentIdentity.AGENT_ID, UUID.randomUUID().toString(), now);
        messages.insertExecution(next, userId, scope.conversationId(), original.sourceMessageId(), nextScope, binding);
        messages.reserveMessage(userId, scope.conversationId(), now);
        failures.linkRetry(userId, next, executionId);
        artifacts.attachScope(userId, nextScope, material.artifactIds(userId, original.sourceMessageId()));
        String payload = RuntimePayloads.toJson(json, Map.of("message", "重新回答原用户消息，保留已有事实。", "sourceMessageId", original.sourceMessageId(), "retryOfExecutionId", executionId));
        commands.insertControl(command, userId, scopeKey, key, hash, null, binding, "MESSAGE_SUBMIT", payload, now);
        var created = scopeMapper.findOwned(userId, nextScope);
        events.publish(created, "command:" + command + ":execution.queued", "execution.queued", Map.of("executionId", next, "sourceMessageId", original.sourceMessageId(), "retryOfExecutionId", executionId, "status", "QUEUED"));
        return new MessageReceipt(scope.conversationId(), original.sourceMessageId(), next, command, ResponseExecutionStatus.QUEUED, scope.conversationId() + ":" + scope.conversationGeneration() + ":" + messages.eventSequence(userId, scope.conversationId()));
    }
}
