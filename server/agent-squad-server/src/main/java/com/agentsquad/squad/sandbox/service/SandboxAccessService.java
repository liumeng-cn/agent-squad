package com.agentsquad.squad.sandbox.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.runtime.service.ExecutionAuthorizationService;
import com.agentsquad.squad.sandbox.domain.SandboxBindingRow;
import com.agentsquad.squad.sandbox.domain.SandboxExecutionContext;
import com.agentsquad.squad.sandbox.repository.SandboxMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 沙箱与交付的运行身份校验。
 * <p>
 * 用户和 Scope 取自原生会话；Worker 额外校验当前计划与业务租约，模型不能替换 guard。
 */
@Service
public class SandboxAccessService {
    @Resource
    private SandboxMapper mapper;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionAuthorizationService authorization;

    public SandboxExecutionContext resolve(String userId, String sessionId, String guardToken,
                                           String turnId, String toolCallId) {
        if (turnId == null || turnId.isBlank() || toolCallId == null || toolCallId.isBlank()
                || turnId.length() > 160 || toolCallId.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "缺少稳定原生调用身份");
        }
        SandboxBindingRow binding = mapper.binding(userId, sessionId);
        if (binding == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "运行绑定不存在");
        }
        SandboxExecutionContext context = new SandboxExecutionContext(userId, binding.scopeId(),
                binding.bindingId(), sessionId, guardToken, turnId, toolCallId);
        requireCurrent(context);
        return context;
    }

    /**
     * 派发和提交均调用；取消、上下文撤销或旧 Worker 失权后禁止发布迟到产物。
     */
    public void requireCurrent(SandboxExecutionContext context) {
        scopes.lockOwned(context.userId(), context.scopeId(), true);
        authorization.requireBindingCurrent(context.userId(), context.bindingId());
        SandboxBindingRow binding = mapper.binding(context.userId(), context.sessionId());
        if (binding == null || !context.scopeId().equals(binding.scopeId())
                || !context.bindingId().equals(binding.bindingId())
                || "WORKER".equals(binding.role()) && (context.guardToken() == null
                || mapper.activeWorker(context.userId(), context.bindingId(), context.guardToken()) != 1)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "运行身份或 Worker 租约已失效");
        }
    }
}
