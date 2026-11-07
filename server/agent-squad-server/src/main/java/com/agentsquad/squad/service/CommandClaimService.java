package com.agentsquad.squad.service;

import com.agentsquad.squad.common.observability.ObservationSupport;
import com.agentsquad.squad.domain.entity.CommandClaimContextDO;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通过短事务领取待处理命令
 * <p>
 * 会话→所有者→命令锁序下复核消息顺序，避免并发扫描、取消与恢复产生倒序或反向锁；事务内不调用SDK。
 */
@Slf4j
@Service
public class CommandClaimService {
    @Resource private UserStatusGuard userStatus;
    @Resource
    private DeliveryMapper deliveries;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private RuntimeAdmissionService admission;

    @Resource
    private ObservationSupport observations;

    @Transactional
    public boolean claim(String commandId, String token, LocalDateTime now, LocalDateTime leaseUntil) {
        CommandClaimContextDO context = deliveries.claimContext(commandId);
        if (context == null) {
            return false;
        }
        userStatus.requireActive(context.userId());
        scopes.lockOwned(context.userId(), context.scopeId(), false);
        if ("MESSAGE_SUBMIT".equals(context.action())
                && !deliveries.earlierBlockingReplies(context.userId(), context.bindingId()).isEmpty()) {
            return false;
        }
        if (deliveries.claim(commandId, token, now, leaseUntil) != 1) {
            return false;
        }

        // 将命令标记为已接手和预留执行容量时，在同一 MySQL 事务中完成。
        // 配额不足时抛出异常，回滚命令领取，不丢失已受理的用户命令。
        admission.acquire(context.userId(), context.bindingId(), commandId, context.action());
        log.info("命令已接手 {}", observations.logFields(Map.of(
                "commandId", commandId, "scopeId", context.scopeId(), "bindingId", context.bindingId(),
                "action", context.action())));
        return true;
    }
}
