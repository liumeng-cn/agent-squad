package com.agentsquad.squad.runtime.domain;

import java.util.Arrays;

/**
 * AgentScope 原生回合的归一化结果。
 * <p>
 * 未识别的原生结果保持 UNKNOWN，平台不得据此推断用户目标已完成。
 */
public enum NativeRunOutcome {
    COMPLETED("completed"),
    FAILED("failed"),
    SUSPENDED("suspended"),
    INTERRUPTED("interrupted"),
    UNKNOWN("unknown");

    private final String code;

    NativeRunOutcome(String code) {
        this.code = code;
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
