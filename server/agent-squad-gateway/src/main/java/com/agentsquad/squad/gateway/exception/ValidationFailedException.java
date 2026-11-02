package com.agentsquad.squad.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 字段合法但内容不满足业务规则，例如用户名格式或密码强度。
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class ValidationFailedException extends RuntimeException {

    public ValidationFailedException(String message) {
        super(message);
    }
}
