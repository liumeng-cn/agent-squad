package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 用户可见错误类别，不包含原始存储异常与敏感数据。
 */
@Getter
public enum RuntimeExceptionCode {
    NOT_FOUND(1, "NOT_FOUND", "未找到"),
    FORBIDDEN(2, "FORBIDDEN", "禁止访问"),
    CONFLICT(3, "CONFLICT", "状态冲突"),
    EXPIRED(4, "EXPIRED", "已过期"),
    VALIDATION(5, "VALIDATION", "校验失败");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    RuntimeExceptionCode(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
