package com.agentsquad.squad.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.po.RateBucketPO;

import com.agentsquad.squad.bo.AuthAccountBO;

import com.agentsquad.squad.bo.AuthSessionBO;

import com.agentsquad.squad.mapper.AuthMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

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

    public RateBucketPO lockLoginBucket(String bucketKey) {
        return authMapper.findBucketForUpdate(bucketKey);
    }

    public void recordLoginFailure(String bucketKey, LocalDateTime now) {
        authMapper.recordLoginFailure(bucketKey, now);
    }

    public void resetLoginFailures(String bucketKey, LocalDateTime now) {
        authMapper.resetLoginFailures(bucketKey, now);
    }

    public Optional<AuthAccountBO> findAccountByUsername(String username) {
        return Optional.ofNullable(authMapper.findAccountByUsername(username));
    }

    public Optional<AuthSessionBO> findSessionByTokenHash(String tokenHash) {
        return Optional.ofNullable(authMapper.findSessionByTokenHash(tokenHash));
    }

    @Transactional
    public void insertSession(AuthSessionBO session, LocalDateTime lastSeenAt) {
        authMapper.insertSession(session, lastSeenAt);
    }

    public void touchSession(String sessionId, LocalDateTime lastSeenAt, LocalDateTime idleExpiresAt) {
        authMapper.touchSession(sessionId, lastSeenAt, idleExpiresAt);
    }

    public void revokeSession(String sessionId, LocalDateTime now) {
        authMapper.revokeSession(sessionId, now);
    }

    public void revokeUserSessions(Long userId, LocalDateTime now) {
        authMapper.revokeUserSessions(userId, now);
    }

    public void insertAudit(Long userId, Long actorUserId, String eventType, String result,
                            String requestId) {
        authMapper.insertAudit(IdWorker.getId(), userId, actorUserId, eventType, result,
                LocalDateTime.now(java.time.ZoneOffset.UTC), requestId == null ? "" : requestId);
    }

    public int deleteExpiredSessions(LocalDateTime now) {
        return authMapper.deleteExpiredSessions(now);
    }

    public int deleteStaleRateBuckets(LocalDateTime before) {
        return authMapper.deleteStaleRateBuckets(before);
    }
}
