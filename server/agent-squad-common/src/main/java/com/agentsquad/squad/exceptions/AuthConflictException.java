package com.agentsquad.squad.exceptions;


/**
 * 乐观锁冲突或状态冲突。
 */
public class AuthConflictException extends RuntimeException {
    public AuthConflictException() {
        super("账户状态已变化");
    }
}
