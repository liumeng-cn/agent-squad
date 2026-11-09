package com.agentsquad.squad.enums;

import java.util.Arrays;
import lombok.Getter;

/**
 * AgentScope 原生回合的归一化结果。
 * <p>
 * 未识别的原生结果保持 UNKNOWN，平台不得据此推断用户目标已完成。
 */
@Getter
public enum NativeRunOutcome {
    COMPLETED("completed", "COMPLETED", "已完成"),
    FAILED("failed", "FAILED", "已失败"),
    SUSPENDED("suspended", "SUSPENDED", "已挂起"),
    INTERRUPTED("interrupted", "INTERRUPTED", "已中断"),
    UNKNOWN("unknown", "UNKNOWN", "未知");

    /** 原生结果编码 */
    private final String code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    NativeRunOutcome(String code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public String code() {
        return code;
    }

    public static NativeRunOutcome fromCode(String code) {
        return Arrays.stream(values())
                .filter(outcome -> outcome.code.equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
