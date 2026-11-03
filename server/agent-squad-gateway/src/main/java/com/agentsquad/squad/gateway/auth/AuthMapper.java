package com.agentsquad.squad.gateway.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 网关认证 SQL 边界。
 *
 * <p>用户、会话、限流与审计全部通过 MyBatis 访问；SQL 见 mapper/auth/AuthMapper.xml。</p>
 */
@Mapper
public interface AuthMapper {

    int ensureLoginBucket(@Param("bucketKey") String bucketKey, @Param("now") LocalDateTime now);

    int recordLoginFailure(@Param("bucketKey") String bucketKey, @Param("now") LocalDateTime now);

    int resetLoginFailures(@Param("bucketKey") String bucketKey, @Param("now") LocalDateTime now);

    AuthAccount findAccountByUsername(@Param("username") String username);

    AuthSessionRow findSessionByTokenHash(@Param("tokenHash") String tokenHash);

    int insertSession(@Param("session") AuthSessionRow session, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    int touchSession(@Param("sessionId") String sessionId, @Param("lastSeenAt") LocalDateTime lastSeenAt,
                     @Param("idleExpiresAt") LocalDateTime idleExpiresAt);

    int revokeSession(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now);

    int revokeUserSessions(@Param("userId") String userId, @Param("now") LocalDateTime now);

    RateBucket findBucketForUpdate(@Param("bucketKey") String bucketKey);

    int insertAudit(@Param("eventId") String eventId, @Param("userId") String userId,
                    @Param("actorUserId") String actorUserId, @Param("eventType") String eventType,
                    @Param("result") String result, @Param("occurredAt") LocalDateTime occurredAt,
                    @Param("requestId") String requestId);

    int deleteExpiredSessions(@Param("now") LocalDateTime now);

    int deleteStaleRateBuckets(@Param("before") LocalDateTime before);
}
