package com.agentsquad.squad.common.exception;

/**
 * 字段合法但内容不满足业务规则，例如用户名格式或密码强度。
 */
public class ValidationFailedException extends RuntimeException {

    public ValidationFailedException(String message) {
        super(message);
    }
}
