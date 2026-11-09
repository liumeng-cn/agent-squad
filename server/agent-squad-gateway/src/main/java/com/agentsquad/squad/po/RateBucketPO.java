package com.agentsquad.squad.po;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 登录限流行快照。
 */
@TableName("sq_auth_rate_bucket")
public record RateBucketPO(LocalDateTime windowStart, int failures, LocalDateTime lockedUntil) {
}
