package com.agentsquad.squad.memory.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.memory.domain.MemoryRow;
import com.agentsquad.squad.memory.domain.MemorySource;
import com.agentsquad.squad.memory.domain.MemoryWriteReceiptRow;
import com.agentsquad.squad.memory.dto.MemoryCreate;
import com.agentsquad.squad.memory.dto.MemoryMutationResult;
import com.agentsquad.squad.memory.dto.MemoryUpdate;
import com.agentsquad.squad.memory.dto.MemoryWriteReceipt;
import com.agentsquad.squad.memory.dto.MemoryWriteRequest;
import com.agentsquad.squad.memory.repository.MemoryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 回复Master的显式记忆写入编排。
 * <p>
 * 从SDK会话反查受权用户消息并保存原话，Worker及任务协调不能写入；幂等回执不重复保存正文。
 */
@Service
public class ExplicitMemoryWriteService {
    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private MemoryMapper mapper;

    @Resource
    private MemoryService memories;

    @Resource
    private MemoryContextService contexts;

    @Resource
    private ObjectMapper json;

    /**
     * 会话/所有者→用户记忆代际的锁序；先恢复稳定回执，再校验当前上下文并执行一次变更。
     */
    @Transactional
    public MemoryWriteReceipt write(String userId, String sessionId, MemoryWriteRequest request) {
        validateShape(request);
        String scopeId = scopes.scopeForSession(userId, sessionId);
        ExecutionScopeRow scope = scopes.lockOwned(userId, scopeId, true);
        if (!"RESPONSE".equals(scope.kind())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "只有受权用户消息的回复可以写入显式记忆");
        }
        mapper.ensureRevision(userId);
        mapper.lockRevision(userId);
        String payload = RuntimePayloads.toJson(json, request);
        String hash = CanonicalJson.sha256(json, payload);
        MemoryWriteReceiptRow prior = mapper.receipt(userId, scopeId, request.logicalKey());
        if (prior != null) {
            if (!prior.payloadHash().equals(hash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同记忆逻辑键的输入不同");
            }
            return readReceipt(prior.resultJson());
        }
        contexts.requireCurrent(userId, scopeId);
        MemorySource source = mapper.source(userId, scopeId);
        if (source == null || !source.content().contains(request.sourceQuote())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "显式记忆必须引用本次受权用户消息中的原话");
        }

        MemoryMutationResult result = apply(userId, request, source);
        MemoryWriteReceipt receipt = new MemoryWriteReceipt(result.memory().memoryId(), result.memory().version(),
                result.memoryRevision(), result.affectedScopes(), result.impact());
        mapper.insertReceipt(userId, scopeId, request.logicalKey(), hash, RuntimePayloads.toJson(json, receipt));
        return receipt;
    }

    private MemoryMutationResult apply(String userId, MemoryWriteRequest request, MemorySource source) {
        if ("CREATE".equals(request.action())) {
            if (request.expectedVersion() != null) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "创建记忆不接受旧版本");
            }
            return memories.createFromSource(userId, new MemoryCreate(request.key(), request.content(), request.expiresAt()),
                    source.messageId(), request.sourceQuote());
        }
        MemoryRow current = mapper.findActiveKey(userId, request.key());
        if (current == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "待纠正或删除的记忆不存在");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "纠正或删除记忆必须提供读取时的版本");
        }
        if ("DELETE".equals(request.action())) {
            return memories.delete(userId, current.memoryId(), request.expectedVersion());
        }
        return memories.updateFromSource(userId, current.memoryId(),
                new MemoryUpdate(request.expectedVersion(), request.content(), request.expiresAt()),
                source.messageId(), request.sourceQuote());
    }

    private void validateShape(MemoryWriteRequest request) {
        if (request == null || request.logicalKey() == null || request.logicalKey().isBlank()
                || request.logicalKey().length() > 160 || request.action() == null || !Set.of("CREATE", "UPDATE", "DELETE").contains(request.action())
                || request.key() == null || request.key().isBlank() || request.key().length() > 100
                || request.sourceQuote() == null || request.sourceQuote().isBlank() || request.sourceQuote().length() > 20000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "显式记忆动作、逻辑键、记忆键或用户原话无效");
        }
    }

    private MemoryWriteReceipt readReceipt(String value) {
        try {
            return json.treeToValue(RuntimePayloads.readJson(json, value), MemoryWriteReceipt.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("显式记忆持久收据损坏", exception);
        }
    }
}
