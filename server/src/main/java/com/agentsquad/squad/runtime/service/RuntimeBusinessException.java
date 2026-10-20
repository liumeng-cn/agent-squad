package com.agentsquad.squad.runtime.service;

/**
 * 运行受理业务错误，由 HTTP 边界统一转换状态码。
 */
public class RuntimeBusinessException extends RuntimeException {

    private final RuntimeExceptionCode code;

    public RuntimeBusinessException(RuntimeExceptionCode code, String message) {
        super(message);
        this.code = code;
    }

    public RuntimeExceptionCode code() {
        return code;
    }
}
