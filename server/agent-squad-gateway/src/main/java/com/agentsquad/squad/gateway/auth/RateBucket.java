package com.agentsquad.squad.gateway.auth;

import java.time.LocalDateTime;

/**
 * 登录限流行快照。
 */
public record RateBucket(LocalDateTime windowStart, int failures, LocalDateTime lockedUntil) {
}
