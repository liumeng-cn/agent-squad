package com.agentsquad.squad.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 唯一键冲突、版本冲突或违反最后一个管理员约束。
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class StateConflictException extends RuntimeException {

    public StateConflictException(String message) {
        super(message);
    }
}
