package com.agentsquad.squad.exceptions;

/**
 * 账号仍使用临时密码，除改密、当前主体与退出外的请求被拒绝。
 */
public class PasswordChangeRequiredException extends RuntimeException {

    public PasswordChangeRequiredException() {
        super("请先修改临时密码");
    }
}
