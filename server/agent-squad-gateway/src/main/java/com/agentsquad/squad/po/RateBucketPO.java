package com.agentsquad.squad.po;

import java.time.LocalDateTime;

/**
 * 登录限流行快照。
 */
public record RateBucketPO(LocalDateTime windowStart, int failures, LocalDateTime lockedUntil) {
}
