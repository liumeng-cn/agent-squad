package com.agentsquad.squad.exceptions;


/** 已登录但不具备管理授权。 */
public class AuthForbiddenException extends RuntimeException {
    public AuthForbiddenException() { super("无权管理用户"); }
}
