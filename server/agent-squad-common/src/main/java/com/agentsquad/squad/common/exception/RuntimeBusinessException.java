package com.agentsquad.squad.common.exception;

import com.agentsquad.squad.domain.enums.RuntimeExceptionCode;

/**
 * 运行受理业务错误，由 HTTP 异常处理器统一转换状态码
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
