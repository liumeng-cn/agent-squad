package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.memory.service.MemoryContextService;
import com.agentsquad.squad.runtime.domain.ExecutionContextInvalidatedException;
import com.agentsquad.squad.runtime.dto.ExecutionContextSnapshot;
import com.agentsquad.squad.skill.service.SkillRuntimeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行授权与固定资料闸门。
 * <p>
 * 从服务端绑定确定作用域，统一检查技能撤销、记忆修订及有效期；失效后不刷新旧引用继续运行。
 */
@Service
public class ExecutionAuthorizationService {
    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private SkillRuntimeService skills;

    @Resource
    private MemoryContextService memories;

    /**
     * 首次派发冻结目录与记忆；重放只读取原固定引用，授权变更则拒绝恢复旧 Session。
     * 锁顺序为会话/所有者、技能授权、记忆代际，事务内不调用模型或沙箱。
     */
    @Transactional
    public ExecutionContextSnapshot prepare(String userId, String scopeId) {
        scopes.lockOwned(userId, scopeId, true);
        validate(userId, scopeId);
        var catalog = skills.freeze(userId, scopeId);
        var memory = memories.freeze(userId, scopeId);
        validate(userId, scopeId);
        return new ExecutionContextSnapshot(catalog, memory);
    }

    /**
     * 复验固定引用，允许在结果发布事务中调用；不以新版本替换已经进入原生历史的旧内容。
     */
    @Transactional
    public void requireScopeCurrent(String userId, String scopeId) {
        if (scopeMapper.findOwned(userId, scopeId) == null) {
            scopes.requireReadable(userId, scopeId);
        }
        if (scopeMapper.findActiveOwned(userId, scopeId) != null) {
            validate(userId, scopeId);
        }
    }

    @Transactional
    public void requireSessionCurrent(String userId, String sessionId) {
        requireScopeCurrent(userId, scopes.scopeForSession(userId, sessionId));
    }

    @Transactional
    public void requireBindingCurrent(String userId, String bindingId) {
        String scopeId = scopeMapper.findScopeByBinding(userId, bindingId);
        requireScopeCurrent(userId, scopeId);
    }

    private void validate(String userId, String scopeId) {
        try {
            skills.validate(userId, scopeId);
            memories.requireCurrent(userId, scopeId);
        } catch (com.agentsquad.squad.common.exception.RuntimeBusinessException exception) {
            throw new ExecutionContextInvalidatedException(exception);
        }
    }
}
