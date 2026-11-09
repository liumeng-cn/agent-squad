package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 计划必要条件的检查结论
 * <p>
 * 未知不等于检查通过；这里只接收结论，不替代产生证据的验收过程。
 */
@Getter
public enum PlanAcceptanceVerdict {
    PASS(1, "PASS", "通过"),
    FAIL(2, "FAIL", "不通过"),
    UNKNOWN(3, "UNKNOWN", "未知");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    PlanAcceptanceVerdict(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
