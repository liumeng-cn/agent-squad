package com.agentsquad.squad.gateway.auth;

import java.time.LocalDateTime;

/**
 * 持久会话与所属账号的联合投影，用于每次受权请求校验状态与授权代际。
 */
public record AuthSessionRow(String sessionId, String userId, String username, String role,
                             boolean mustChangePassword, String tokenHash, String csrfHash,
                             long issuedAuthVersion, long accountAuthVersion, String accountStatus,
                             LocalDateTime lastSeenAt, LocalDateTime idleExpiresAt,
                             LocalDateTime absoluteExpiresAt, LocalDateTime revokedAt) {
}
