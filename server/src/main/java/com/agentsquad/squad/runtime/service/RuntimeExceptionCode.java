package com.agentsquad.squad.runtime.service;

/**
 * 用户可见错误类别，不包含原始存储异常与敏感数据。
 */
public enum RuntimeExceptionCode {
    NOT_FOUND,
    CONFLICT,
    VALIDATION
}
