package com.agentsquad.squad.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.observer.ExecutionTrace;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.model.po.ConversationPO;
import com.agentsquad.squad.model.bo.ExecutionOwnerBO;
import com.agentsquad.squad.model.po.ExecutionScopePO;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 回复与目标的执行范围管理
 * <p>
 * 从服务端会话绑定定位Scope，串行化会话与实际所有者，控制新派发；财务结算允许已终结或删除的所有者出账。
 */
@Service
public class ExecutionScopeService {
    @Resource
    private ExecutionTrace executionTrace;

    private static final Set<String> DISPATCHABLE = Set.of("QUEUED", "RUNNING", "RECOVERING", "WAITING_WORKERS", "WAITING_INPUT", "WAITING_APPROVAL");

    @Resource
    private ExecutionScopeMapper mapper;

    @Resource
    private ConversationMapper conversations;

    /**
     * 目标受理事务中创建固定账户与Scope；不预留花费、不派发模型，事务失败全部回滚。
     */
    @Transactional
    public Long createTaskScope(Long userId, Long taskId, Long conversationId, long generation,
                                  String profileKey, long limit) {
        ConversationPO conversation = conversations.lockOwned(userId, conversationId);
        if (conversation == null || !"ACTIVE".equals(conversation.getStatus()) || conversation.getGeneration() != generation) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标所属会话已失效");
        }
        Long accountId = IdWorker.getId();
        Long scopeId = IdWorker.getId();
        mapper.insertAccount(userId, accountId, profileKey, limit);
        mapper.insertScope(scopeId, userId, conversationId, generation, "TASK", taskId, accountId);
        executionTrace.capture(userId, scopeId);
        if (mapper.linkTaskScope(userId, taskId, scopeId) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标执行归属已变化");
        }
        return scopeId;
    }

    public ExecutionScopePO requireReadable(Long userId, Long scopeId) {
        ExecutionScopePO scope = mapper.findActiveOwned(userId, scopeId);
        if (scope == null) {
            throw missing();
        }
        return scope;
    }

    /**
     * 固定会话→所有者锁序；结算可越过生命周期终态，但仍必须满足原用户与Scope归属。
     */
    @Transactional
    public ExecutionScopePO lockOwned(Long userId, Long scopeId, boolean forDispatch) {
        ExecutionScopePO scope = mapper.findOwned(userId, scopeId);
        if (scope == null) {
            throw missing();
        }
        ConversationPO conversation = conversations.lockOwned(userId, scope.getConversationId());
        if (conversation == null) {
            throw missing();
        }
        ExecutionOwnerBO owner = "TASK".equals(scope.getKind())
                ? mapper.lockTaskOwner(userId, scope.getOwnerId(), scopeId)
                : mapper.lockResponseOwner(userId, scope.getOwnerId(), scopeId);
        if (owner == null) {
            throw missing();
        }
        if (forDispatch && (!"ACTIVE".equals(conversation.getStatus())
                || conversation.getGeneration() != scope.getConversationGeneration()
                || !"0".equals(owner.getDeleted()) || owner.isCancellationRequested()
                || !DISPATCHABLE.contains(owner.getStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "执行已停止、受限或上下文失效，不再接受新操作");
        }
        return scope;
    }

    /**
     * 工具执行身份只从SDK会话映射，模型不能自选Scope或目标。
     */
    public Long scopeForSession(Long userId, String sessionId) {
        Long scopeId = mapper.findScopeBySession(userId, sessionId);
        if (scopeId == null) {
            throw missing();
        }
        return scopeId;
    }

    public Long scopeForTask(Long userId, Long taskId) {
        Long scopeId = mapper.findScopeByTask(userId, taskId);
        if (scopeId == null) {
            throw missing();
        }
        return scopeId;
    }

    private RuntimeBusinessException missing() {
        return new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "执行不存在或无权访问");
    }
}
