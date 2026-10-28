package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.domain.ExecutionOwnerRow;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * 回复与目标的统一执行归属边界。
 * <p>
 * 从服务端会话绑定定位Scope，串行化会话与实际所有者，控制新派发；财务结算允许已终结或删除的所有者出账。
 */
@Service
public class ExecutionScopeService {

    private static final Set<String> DISPATCHABLE = Set.of("QUEUED", "RUNNING", "RECOVERING", "WAITING_WORKERS", "WAITING_INPUT", "WAITING_APPROVAL");

    @Resource
    private ExecutionScopeMapper mapper;

    @Resource
    private ConversationMapper conversations;

    /**
     * 目标受理事务中创建固定账户与Scope；不预留花费、不派发模型，事务失败全部回滚。
     */
    @Transactional
    public String createTaskScope(String userId, String taskId, String conversationId, long generation,
                                  String profileKey, long limit) {
        ConversationRow conversation = conversations.lockOwned(userId, conversationId);
        if (conversation == null || !"ACTIVE".equals(conversation.status()) || conversation.generation() != generation) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标所属会话已失效");
        }
        String accountId = UUID.randomUUID().toString();
        String scopeId = UUID.randomUUID().toString();
        mapper.insertAccount(userId, accountId, profileKey, limit);
        mapper.insertScope(scopeId, userId, conversationId, generation, "TASK", taskId, accountId);
        if (mapper.linkTaskScope(userId, taskId, scopeId) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标执行归属已变化");
        }
        return scopeId;
    }

    public ExecutionScopeRow requireReadable(String userId, String scopeId) {
        ExecutionScopeRow scope = mapper.findActiveOwned(userId, scopeId);
        if (scope == null) {
            throw missing();
        }
        return scope;
    }

    /**
     * 固定会话→所有者锁序；结算可越过生命周期终态，但仍必须满足原用户与Scope归属。
     */
    @Transactional
    public ExecutionScopeRow lockOwned(String userId, String scopeId, boolean forDispatch) {
        ExecutionScopeRow scope = mapper.findOwned(userId, scopeId);
        if (scope == null) {
            throw missing();
        }
        ConversationRow conversation = conversations.lockOwned(userId, scope.conversationId());
        if (conversation == null) {
            throw missing();
        }
        ExecutionOwnerRow owner = "TASK".equals(scope.kind())
                ? mapper.lockTaskOwner(userId, scope.ownerId(), scopeId)
                : mapper.lockResponseOwner(userId, scope.ownerId(), scopeId);
        if (owner == null) {
            throw missing();
        }
        if (forDispatch && (!"ACTIVE".equals(conversation.status())
                || conversation.generation() != scope.conversationGeneration()
                || !"0".equals(owner.deleted()) || owner.cancellationRequested()
                || !DISPATCHABLE.contains(owner.status()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "执行已停止、受限或上下文失效，不再接受新操作");
        }
        return scope;
    }

    /**
     * 工具执行身份只从SDK会话映射，模型不能自选Scope或目标。
     */
    public String scopeForSession(String userId, String sessionId) {
        String scopeId = mapper.findScopeBySession(userId, sessionId);
        if (scopeId == null) {
            throw missing();
        }
        return scopeId;
    }

    public String scopeForTask(String userId, String taskId) {
        String scopeId = mapper.findScopeByTask(userId, taskId);
        if (scopeId == null) {
            throw missing();
        }
        return scopeId;
    }

    private RuntimeBusinessException missing() {
        return new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "执行不存在或无权访问");
    }
}
