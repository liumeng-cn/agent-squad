package com.agentsquad.squad.gateway.auth;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 网关认证持久层。
 *
 * <p>只通过 MyBatis Mapper 访问用户、会话、登录限流和审计表；所有方法均阻塞，
 * 调用方必须在线程池执行，不得在 Reactor event-loop 直接调用。</p>
 */
@Repository
public class AuthRepository {

    @Resource
    private AuthMapper authMapper;

    public Optional<AuthAccount> findAccountByUsername(String username) {
        return Optional.ofNullable(authMapper.findAccountByUsername(username));
    }

    public Optional<AuthSessionRow> findSessionByTokenHash(String tokenHash) {
        return Optional.ofNullable(authMapper.findSessionByTokenHash(tokenHash));
    }

    @Transactional
    public void insertSession(AuthSessionRow session, LocalDateTime lastSeenAt) {
        authMapper.insertSession(session, lastSeenAt);
    }

    public void touchSession(String sessionId, LocalDateTime lastSeenAt, LocalDateTime idleExpiresAt) {
        authMapper.touchSession(sessionId, lastSeenAt, idleExpiresAt);
    }

    public void revokeSession(String sessionId, LocalDateTime now) {
        authMapper.revokeSession(sessionId, now);
    }

    public void revokeUserSessions(String userId, LocalDateTime now) {
        authMapper.revokeUserSessions(userId, now);
    }

    public Optional<LocalDateTime> lockedUntil(String bucketKey, LocalDateTime now) {
        LocalDateTime value = authMapper.findLockedUntil(bucketKey);
        return value != null && value.isAfter(now) ? Optional.of(value) : Optional.empty();
    }

    /**
     * 事务内锁行累加失败次数并计算锁定截止时间；窗口过期自动重置。
     */
    @Transactional
    public Optional<LocalDateTime> recordFailure(String bucketKey, LocalDateTime now, AuthProperties properties) {
        RateBucket bucket = authMapper.findBucketForUpdate(bucketKey);
        LocalDateTime windowStart = now;
        int failures = 0;
        LocalDateTime lockedUntil = null;
        if (bucket == null) {
            authMapper.insertBucket(bucketKey, now);
            failures = 1;
        } else {
            lockedUntil = bucket.lockedUntil();
            if (bucket.windowStart().plus(properties.getFailureWindow()).isAfter(now)) {
                windowStart = bucket.windowStart();
                failures = bucket.failures();
            } else {
                lockedUntil = null;
            }
            failures++;
            if (failures >= properties.getMaxFailures()) {
                lockedUntil = now.plus(properties.getLockDuration());
            }
            authMapper.updateBucket(bucketKey, windowStart, failures, lockedUntil);
        }
        if (failures >= properties.getMaxFailures()) {
            lockedUntil = now.plus(properties.getLockDuration());
        }
        return Optional.ofNullable(lockedUntil);
    }

    public void clearFailures(String bucketKey) {
        authMapper.deleteBucket(bucketKey);
    }

    public void insertAudit(String userId, String actorUserId, String eventType, String result,
                            String requestId) {
        authMapper.insertAudit(UUID.randomUUID().toString(), userId, actorUserId, eventType, result,
                LocalDateTime.now(), requestId == null ? "" : requestId);
    }

    public int deleteExpiredSessions(LocalDateTime now) {
        return authMapper.deleteExpiredSessions(now);
    }

    public int deleteStaleRateBuckets(LocalDateTime before) {
        return authMapper.deleteStaleRateBuckets(before);
    }
}
