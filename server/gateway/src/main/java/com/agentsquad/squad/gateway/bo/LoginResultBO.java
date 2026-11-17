package com.agentsquad.squad.gateway.bo;

import com.agentsquad.squad.gateway.vo.LoginVO;
import lombok.Data;

/**
 * 登录内部处理结果
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Data
public class LoginResultBO {
    /**
     * 写入HttpOnly Cookie的JWT，不出现在JSON响应
     */
    private String token;
    /**
     * 可返回浏览器的登录结果
     */
    private LoginVO view;
}
