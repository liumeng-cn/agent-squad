package com.agentsquad.squad.service;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.CanonicalJson;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.po.ExecutionScopePO;
import com.agentsquad.squad.model.po.MemoryPO;
import com.agentsquad.squad.model.bo.MemorySourceBO;
import com.agentsquad.squad.model.po.MemoryWriteReceiptPO;
import com.agentsquad.squad.model.dto.MemoryCreateDTO;
import com.agentsquad.squad.model.vo.MemoryMutationVO;
import com.agentsquad.squad.model.dto.MemoryUpdateDTO;
import com.agentsquad.squad.model.bo.MemoryWriteReceiptBO;
import com.agentsquad.squad.model.bo.MemoryWriteRequestBO;
import com.agentsquad.squad.mapper.MemoryMapper;
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
    public MemoryWriteReceiptBO write(Long userId, String sessionId, MemoryWriteRequestBO request) {
        validateShape(request);
        Long scopeId = scopes.scopeForSession(userId, sessionId);
        ExecutionScopePO scope = scopes.lockOwned(userId, scopeId, true);
        if (!"RESPONSE".equals(scope.getKind())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "只有受权用户消息的回复可以写入显式记忆");
        }
        mapper.ensureRevision(userId);
        mapper.lockRevision(userId);
        String payload = RuntimePayloads.toJson(json, request);
        String hash = CanonicalJson.sha256(json, payload);
        MemoryWriteReceiptPO prior = mapper.receipt(userId, scopeId, request.logicalKey());
        if (prior != null) {
            if (!prior.getPayloadHash().equals(hash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同记忆逻辑键的输入不同");
            }
            return readReceipt(prior.getResultJson());
        }
        contexts.requireCurrent(userId, scopeId);
        MemorySourceBO source = mapper.source(userId, scopeId);
        if (source == null || !source.getContent().contains(request.sourceQuote())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "显式记忆必须引用本次受权用户消息中的原话");
        }

        MemoryMutationVO result = apply(userId, request, source);
        MemoryWriteReceiptBO receipt = new MemoryWriteReceiptBO(result.memory().memoryId(), result.memory().version(),
                result.memoryRevision(), result.affectedScopes(), result.impact());
        mapper.insertReceipt(userId, scopeId, request.logicalKey(), hash, RuntimePayloads.toJson(json, receipt));
        return receipt;
    }

    private MemoryMutationVO apply(Long userId, MemoryWriteRequestBO request, MemorySourceBO source) {
        if ("CREATE".equals(request.action())) {
            if (request.expectedVersion() != null) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "创建记忆不接受旧版本");
            }
            return memories.createFromSource(userId, new MemoryCreateDTO(request.key(), request.content(), request.expiresAt()),
                    source.getMessageId(), request.sourceQuote());
        }
        MemoryPO current = mapper.findActiveKey(userId, request.key());
        if (current == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "待纠正或删除的记忆不存在");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "纠正或删除记忆必须提供读取时的版本");
        }
        if ("DELETE".equals(request.action())) {
            return memories.delete(userId, current.getMemoryId(), request.expectedVersion());
        }
        return memories.updateFromSource(userId, current.getMemoryId(),
                new MemoryUpdateDTO(request.expectedVersion(), request.content(), request.expiresAt()),
                source.getMessageId(), request.sourceQuote());
    }

    private void validateShape(MemoryWriteRequestBO request) {
        if (request == null || request.logicalKey() == null || request.logicalKey().isBlank()
                || request.logicalKey().length() > 160 || request.action() == null || !Set.of("CREATE", "UPDATE", "DELETE").contains(request.action())
                || request.key() == null || request.key().isBlank() || request.key().length() > 100
                || request.sourceQuote() == null || request.sourceQuote().isBlank() || request.sourceQuote().length() > 20000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "显式记忆动作、逻辑键、记忆键或用户原话无效");
        }
    }

    private MemoryWriteReceiptBO readReceipt(String value) {
        try {
            return json.treeToValue(RuntimePayloads.readJson(json, value), MemoryWriteReceiptBO.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("显式记忆持久收据损坏", exception);
        }
    }
}
