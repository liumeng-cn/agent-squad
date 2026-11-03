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

    public void ensureLoginBucket(String bucketKey, LocalDateTime now) {
        authMapper.ensureLoginBucket(bucketKey, now);
    }

    public RateBucket lockLoginBucket(String bucketKey) {
        return authMapper.findBucketForUpdate(bucketKey);
    }

    public void recordLoginFailure(String bucketKey, LocalDateTime now) {
        authMapper.recordLoginFailure(bucketKey, now);
    }

    public void resetLoginFailures(String bucketKey, LocalDateTime now) {
        authMapper.resetLoginFailures(bucketKey, now);
    }

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

    public void insertAudit(String userId, String actorUserId, String eventType, String result,
                            String requestId) {
        authMapper.insertAudit(UUID.randomUUID().toString(), userId, actorUserId, eventType, result,
                LocalDateTime.now(java.time.ZoneOffset.UTC), requestId == null ? "" : requestId);
    }

    public int deleteExpiredSessions(LocalDateTime now) {
        return authMapper.deleteExpiredSessions(now);
    }

    public int deleteStaleRateBuckets(LocalDateTime before) {
        return authMapper.deleteStaleRateBuckets(before);
    }
}
