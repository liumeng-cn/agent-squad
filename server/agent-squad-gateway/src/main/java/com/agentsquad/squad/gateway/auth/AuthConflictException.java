package com.agentsquad.squad.gateway.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 乐观锁冲突或状态冲突。 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AuthConflictException extends RuntimeException {
    public AuthConflictException() { super("账户状态已变化"); }
}
