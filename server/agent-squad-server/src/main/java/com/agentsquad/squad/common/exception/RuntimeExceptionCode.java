package com.agentsquad.squad.common.exception;

/**
 * 用户可见错误类别，不包含原始存储异常与敏感数据。
 */
public enum RuntimeExceptionCode {
    NOT_FOUND,
    FORBIDDEN,
    CONFLICT,
    EXPIRED,
    VALIDATION
}
