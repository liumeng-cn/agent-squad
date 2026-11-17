package com.agentsquad.squad.gateway.vo;

import com.agentsquad.squad.user.api.vo.UserAccountVO;
import lombok.Data;

import java.time.Instant;

/**
 * 浏览器登录结果
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Data
public class LoginVO {
    /**
     * 当前用户资料
     */
    private UserAccountVO user;
    /**
     * 写请求防伪令牌
     */
    private String csrfToken;
    /**
     * 登录到期时间
     */
    private Instant expiresAt;
}
