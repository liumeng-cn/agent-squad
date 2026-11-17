package com.agentsquad.squad.gateway.bo;

import com.agentsquad.squad.user.api.vo.UserAccountVO;
import lombok.Data;

/**
 * 认证成功的用户与会话
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Data
public class AuthUserBO {
    /**
     * 当前账号事实
     */
    private UserAccountVO user;
    /**
     * 当前有效登录会话
     */
    private LoginSessionBO session;
}
