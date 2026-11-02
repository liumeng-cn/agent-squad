package com.agentsquad.squad.gateway.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 已登录但不具备管理授权。 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AuthForbiddenException extends RuntimeException {
    public AuthForbiddenException() { super("无权管理用户"); }
}
