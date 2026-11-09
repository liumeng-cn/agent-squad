package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.InteractionMapper;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.po.CommandPO;
import com.agentsquad.squad.model.po.ConversationPO;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.model.po.ExecutionScopePO;
import com.agentsquad.squad.model.po.ResponseRuntimePO;
import com.agentsquad.squad.model.vo.CommandReceiptVO;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.mapper.ResponseRuntimeMapper;
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
 * 受理停止回答请求并保存处理结果
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
    private ConversationEventWriterService events;

    @Resource
    private OperationService operations;

    @Resource
    private InteractionMapper interactions;

    @Resource
    private ObjectMapper json;

    @Transactional
    public CommandReceiptVO cancel(String userId, String executionId, String key, long expectedVersion) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw error(RuntimeExceptionCode.VALIDATION, "幂等键无效");
        }
        String binding = replies.bindingForExecution(userId, executionId);
        String scopeId = scopeMapper.findScopeByBinding(userId, binding);
        scopes.requireReadable(userId, scopeId);
        ExecutionScopePO scope = scopes.lockOwned(userId, scopeId, false);
        ConversationPO conversation = conversations.lockOwned(userId, scope.getConversationId());
        if (!"ACTIVE".equals(conversation.getStatus()) || conversation.getGeneration() != scope.getConversationGeneration()) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "回复所属会话已失效");
        }
        ResponseRuntimePO row = replies.lockByBinding(userId, binding);
        String payload = RuntimePayloads.toJson(json, Map.of("expectedVersion", expectedVersion));
        String hash = ContentDigests.sha256(payload);
        String scopeKey = "response.cancel:" + executionId;
        CommandPO prior = commands.findByKeyCurrent(userId, scopeKey, key);
        if (prior != null) {
            if (!hash.equals(prior.getPayloadHash())) {
                throw error(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return new CommandReceiptVO(prior.getCommandId(), null, prior.getStatus(), prior.getNativeTurnId(), null);
        }
        if (row.getVersion() != expectedVersion || row.isCancellationRequested()
                || Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(row.getStatus())) {
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
            ExecutionScopePO scope = scopes.lockOwned(userId, scopeMapper.findScopeByBinding(userId, binding), false);
            ResponseRuntimePO row = replies.lockByBinding(userId, binding);
            if (!row.isCancellationRequested()) {
                String payload = RuntimePayloads.toJson(json, Map.of("reason", "conversation-delete"));
                queueStop(scope, row, "response.cancel:" + row.getExecutionId(), "conversation-delete", payload, ContentDigests.sha256(payload));
            }
        }
    }

    private CommandReceiptVO queueStop(ExecutionScopePO scope, ResponseRuntimePO row, String scopeKey, String key, String payload, String hash) {
        replies.cancelling(scope.getUserId(), row.getBindingId(), LocalDateTime.now(ZoneOffset.UTC));
        replies.cancelPending(scope.getUserId(), row.getBindingId());
        operations.cancelUnstarted(scope.getUserId(), scope.getScopeId());
        interactions.cancelOpenByScope(scope.getUserId(), scope.getScopeId());
        String id = UUID.randomUUID().toString();
        commands.insertControl(id, scope.getUserId(), scopeKey, key, hash, null, row.getBindingId(), "STOP", payload, LocalDateTime.now(ZoneOffset.UTC));
        events.publish(scope, "response:" + row.getExecutionId() + ":cancel", "execution.status_changed",
                Map.of("executionId", row.getExecutionId(), "status", "CANCELLING", "version", row.getVersion() + 1));
        return new CommandReceiptVO(id, null, "PENDING", null, null);
    }

    private RuntimeBusinessException error(RuntimeExceptionCode code, String text) {
        return new RuntimeBusinessException(code, text);
    }
}
