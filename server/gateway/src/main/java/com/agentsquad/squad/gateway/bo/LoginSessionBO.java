package com.agentsquad.squad.gateway.bo;

import lombok.Data;

import java.time.Instant;

/**
 * Redis登录会话
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Data
public class LoginSessionBO {
    /**
     * 随机登录会话ID
     */
    private String sessionId;
    /**
     * 用户业务ID
     */
    private String userId;
    /**
     * 颁发时认证版本
     */
    private long authVersion;
    /**
     * CSRF随机令牌，仅在登录及本人会话查询返回
     */
    private String csrfToken;
    /**
     * 登录时间
     */
    private Instant createdAt;
    /**
     * 固定到期时间
     */
    private Instant expiresAt;
}
