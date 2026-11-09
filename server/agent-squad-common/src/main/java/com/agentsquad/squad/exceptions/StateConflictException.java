package com.agentsquad.squad.exceptions;

/**
 * 唯一键冲突、版本冲突或违反最后一个管理员约束。
 */
public class StateConflictException extends RuntimeException {

    public StateConflictException(String message) {
        super(message);
    }
}
