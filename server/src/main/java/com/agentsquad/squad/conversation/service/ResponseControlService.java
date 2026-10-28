package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.CommandRow;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.domain.ResponseRuntimeRow;
import com.agentsquad.squad.conversation.dto.CommandReceipt;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 停止回答的持久受理边界。
 * <p>
 * 只停止指定回复，不取消独立目标；删除会话复用相同停止路径，原生结束前不宣称已停止。
 */
@Service
public class ResponseControlService {
    @Resource
    private ConversationMapper conversations;

    @Resource
    private ResponseRuntimeMapper replies;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private CommandMapper commands;

    @Resource
    private ConversationEventWriter events;

    @Resource
    private ObjectMapper json;

    @Transactional
    public CommandReceipt cancel(String userId, String executionId, String key, long expectedVersion) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw error(RuntimeExceptionCode.VALIDATION, "幂等键无效");
        }
        String binding = replies.bindingForExecution(userId, executionId);
        String scopeId = scopeMapper.findScopeByBinding(userId, binding);
        scopes.requireReadable(userId, scopeId);
        ExecutionScopeRow scope = scopes.lockOwned(userId, scopeId, false);
        ConversationRow conversation = conversations.lockOwned(userId, scope.conversationId());
        if (!"ACTIVE".equals(conversation.status()) || conversation.generation() != scope.conversationGeneration()) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "回复所属会话已失效");
        }
        ResponseRuntimeRow row = replies.lockByBinding(userId, binding);
        String payload = RuntimePayloads.toJson(json, Map.of("expectedVersion", expectedVersion));
        String hash = ContentDigests.sha256(payload);
        String scopeKey = "response.cancel:" + executionId;
        CommandRow prior = commands.findByKeyCurrent(userId, scopeKey, key);
        if (prior != null) {
            if (!hash.equals(prior.payloadHash())) {
                throw error(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return new CommandReceipt(prior.commandId(), null, prior.status(), prior.nativeTurnId(), null);
        }
        if (row.version() != expectedVersion || row.cancellationRequested()
                || Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(row.status())) {
            throw error(RuntimeExceptionCode.CONFLICT, "回答状态已变化");
        }
        return queueStop(scope, row, scopeKey, key, payload, hash);
    }

    /**
     * 会话删除事务已经持有会话锁；逐个回复锁定所有者并保存停止意图，不清理未知操作。
     */
    @Transactional
    public void cancelForDeletion(String userId, String conversationId) {
        for (String binding : replies.activeBindings(userId, conversationId)) {
            ExecutionScopeRow scope = scopes.lockOwned(userId, scopeMapper.findScopeByBinding(userId, binding), false);
            ResponseRuntimeRow row = replies.lockByBinding(userId, binding);
            if (!row.cancellationRequested()) {
                String payload = RuntimePayloads.toJson(json, Map.of("reason", "conversation-delete"));
                queueStop(scope, row, "response.cancel:" + row.executionId(), "conversation-delete", payload, ContentDigests.sha256(payload));
            }
        }
    }

    private CommandReceipt queueStop(ExecutionScopeRow scope, ResponseRuntimeRow row, String scopeKey, String key, String payload, String hash) {
        replies.cancelling(scope.userId(), row.bindingId(), LocalDateTime.now(ZoneOffset.UTC));
        replies.cancelPending(scope.userId(), row.bindingId());
        String id = UUID.randomUUID().toString();
        commands.insertControl(id, scope.userId(), scopeKey, key, hash, null, row.bindingId(), "STOP", payload, LocalDateTime.now(ZoneOffset.UTC));
        events.publish(scope, "response:" + row.executionId() + ":cancel", "execution.status_changed",
                Map.of("executionId", row.executionId(), "status", "CANCELLING", "version", row.version() + 1));
        return new CommandReceipt(id, null, "PENDING", null, null);
    }

    private RuntimeBusinessException error(RuntimeExceptionCode code, String text) {
        return new RuntimeBusinessException(code, text);
    }
}
