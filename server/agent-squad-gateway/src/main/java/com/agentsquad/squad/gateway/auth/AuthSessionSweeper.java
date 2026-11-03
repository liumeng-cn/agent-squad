package com.agentsquad.squad.gateway.auth;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 过期会话与限流窗口清理。
 *
 * <p>在调度线程分批删除，避免长事务和 event-loop 阻塞；只清理过期或撤销的认证数据，
 * 不触碰业务表和用户账号。</p>
 */
@Slf4j
@Component
public class AuthSessionSweeper {

    private static final int BATCH_SIZE = 1000;

    @Resource
    private AuthRepository authRepository;

    @Resource
    private AuthProperties authProperties;

    @Scheduled(fixedDelayString = "${agentsquad.auth.cleanup-interval-ms:600000}",
            initialDelayString = "${agentsquad.auth.cleanup-initial-delay-ms:60000}")
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        int sessions = drain(() -> authRepository.deleteExpiredSessions(now));
        LocalDateTime bucketBefore = now.minus(authProperties.getFailureWindow().multipliedBy(2))
                .minus(authProperties.getLockDuration());
        int buckets = drain(() -> authRepository.deleteStaleRateBuckets(bucketBefore));
        if (sessions + buckets > 0) {
            log.info("认证数据清理完成 expiredSessions={} staleBuckets={}", sessions, buckets);
        }
    }

    private int drain(java.util.function.IntSupplier delete) {
        int total = 0;
        int deleted;
        do {
            deleted = delete.getAsInt();
            total += deleted;
        } while (deleted >= BATCH_SIZE);
        return total;
    }
}
