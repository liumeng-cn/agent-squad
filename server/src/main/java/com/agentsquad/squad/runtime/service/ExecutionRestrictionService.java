package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.conversation.service.ConversationEventWriter;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.runtime.domain.FailureClassification;
import com.agentsquad.squad.runtime.repository.ExecutionRestrictionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 旧执行上下文的安全收尾。
 * <p>
 * 以幂等状态变更保留失败事实，阻止继续恢复；用户资料和已发生操作均保留，不伪造撤回。
 */
@Service
@Slf4j
public class ExecutionRestrictionService {
    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionRestrictionMapper mapper;

    @Resource
    private ExecutionFailureService failures;

    @Resource
    private ConversationEventWriter events;

    /**
     * 共享会话锁下原子写阻塞、命令与安全故障事件；重复发现不重复修改版本或追加状态事件。
     */
    @Transactional
    public void restrict(String userId, String scopeId, String bindingId) {
        var scope = scopes.lockOwned(userId, scopeId, false);
        int changed;
        if ("RESPONSE".equals(scope.kind())) {
            changed = mapper.failResponse(userId, scopeId);
        } else {
            changed = mapper.blockTask(userId, scopeId);
            mapper.blockJobs(userId, scope.ownerId());
        }
        mapper.blockCommands(userId, scopeId);
        if (changed == 0) {
            return;
        }
        failures.record(scope, bindingId, "context-invalidated:" + scopeId,
                new FailureClassification("AUTH_REQUIRED", "EXECUTION_CONTEXT_INVALIDATED", "SYSTEM",
                        "技能授权或记忆已变化，已停止使用旧上下文。已完成内容仍保留，请重新发起。"));
        events.publish(scope, "context-restricted:" + scopeId, "execution.status_changed",
                Map.of("scopeId", scopeId, "status", "RESPONSE".equals(scope.kind()) ? "FAILED" : "RECOVERY_BLOCKED"));
        log.info("执行上下文已失效并限制继续运行 scopeId={} bindingId={} kind={}", scopeId, bindingId, scope.kind());
    }
}
