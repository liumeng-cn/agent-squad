package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 平台支持的程序检查项
 * <p>
 * 只验证明确定义的数据库事实，不根据自由文字猜测或执行模型生成的检查代码。
 */
@Getter
public enum DeterministicAcceptanceCheck {
    REQUIRED_RESULTS_READY(1, "REQUIRED_RESULTS_READY", "必需结果已就绪");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    DeterministicAcceptanceCheck(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
