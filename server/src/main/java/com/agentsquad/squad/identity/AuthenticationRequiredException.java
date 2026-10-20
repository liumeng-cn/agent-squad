package com.agentsquad.squad.identity;

/** 登录会话缺失、失效或凭据不匹配。 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("登录已失效或凭据错误");
    }
}
