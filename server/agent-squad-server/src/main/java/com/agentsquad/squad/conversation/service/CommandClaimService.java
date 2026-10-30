package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.conversation.domain.CommandClaimContext;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Bridge命令领取的短事务边界。
 * <p>
 * 会话→所有者→命令锁序下复核消息顺序，避免并发扫描、取消与恢复产生倒序或反向锁；事务内不调用SDK。
 */
@Service
public class CommandClaimService {
    @Resource
    private DeliveryMapper deliveries;

    @Resource
    private ExecutionScopeService scopes;

    @Transactional
    public boolean claim(String commandId, String token, LocalDateTime now, LocalDateTime leaseUntil) {
        CommandClaimContext context = deliveries.claimContext(commandId);
        if (context == null) {
            return false;
        }
        scopes.lockOwned(context.userId(), context.scopeId(), false);
        if ("MESSAGE_SUBMIT".equals(context.action())
                && !deliveries.earlierBlockingReplies(context.userId(), context.bindingId()).isEmpty()) {
            return false;
        }
        return deliveries.claim(commandId, token, now, leaseUntil) == 1;
    }
}
