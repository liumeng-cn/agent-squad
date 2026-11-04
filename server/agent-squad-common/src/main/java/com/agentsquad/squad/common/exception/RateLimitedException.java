package com.agentsquad.squad.common.exception;

/**
 * 登录失败次数超出窗口阈值，账号或来源被临时锁定。
 */
public class RateLimitedException extends RuntimeException {

    public RateLimitedException() {
        super("登录尝试过于频繁，请稍后再试");
    }
}
