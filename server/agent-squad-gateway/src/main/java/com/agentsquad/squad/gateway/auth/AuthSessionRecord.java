package com.agentsquad.squad.gateway.auth;

import java.time.LocalDateTime;

/** 数据库会话投影；token_hash 不包含原始 Cookie。 */
public record AuthSessionRecord(String userId, long issuedAuthVersion, LocalDateTime lastSeenAt,
                                LocalDateTime idleExpiresAt, LocalDateTime absoluteExpiresAt) {
}
