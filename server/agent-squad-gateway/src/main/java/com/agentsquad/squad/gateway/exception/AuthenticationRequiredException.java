package com.agentsquad.squad.gateway.exception;

/**
 * 登录会话缺失、失效或凭据不匹配。
 */
@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("登录已失效或凭据错误");
    }
}
