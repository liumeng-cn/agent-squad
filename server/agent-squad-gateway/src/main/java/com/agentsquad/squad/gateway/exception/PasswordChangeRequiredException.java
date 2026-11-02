package com.agentsquad.squad.gateway.exception;

/**
 * 账号仍使用临时密码，除改密、当前主体与退出外的请求被拒绝。
 */
@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
public class PasswordChangeRequiredException extends RuntimeException {

    public PasswordChangeRequiredException() {
        super("请先修改临时密码");
    }
}
