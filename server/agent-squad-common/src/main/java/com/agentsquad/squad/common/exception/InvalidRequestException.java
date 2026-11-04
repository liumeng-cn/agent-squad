package com.agentsquad.squad.common.exception;

/**
 * 请求缺少必要字段或字段格式错误。
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
