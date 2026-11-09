package com.agentsquad.squad.scheduler;


import com.agentsquad.squad.service.AuthRepository;

import com.agentsquad.squad.config.properties.AuthProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 认证会话清理定时任务
 *
 * <p>分批清理过期或已撤销的登录会话及陈旧的登录限流记录。</p>
 */
@Slf4j
@Component
public class AuthSessionCleanupTask {

    private static final int BATCH_SIZE = 1000;

    @Resource
    private AuthRepository authRepository;

    @Resource
    private AuthProperties authProperties;

    /**
     * 以本轮开始时间为基准清理会话，并按限流窗口与锁定时长计算限流记录的清理边界。
     */
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
