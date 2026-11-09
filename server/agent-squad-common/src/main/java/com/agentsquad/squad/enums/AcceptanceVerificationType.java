package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 完成条件的检查方式
 * <p>
 * 程序检查、模型评价与用户确认分别记录，不将模型评价伪装成程序验证。
 */
@Getter
public enum AcceptanceVerificationType {
    DETERMINISTIC(1, "DETERMINISTIC", "程序检查"),
    MODEL_REVIEW(2, "MODEL_REVIEW", "模型评价"),
    USER_REVIEW(3, "USER_REVIEW", "用户确认");

    /**
     * 编码
     */
    private final int code;
    /**
     * 英文名
     */
    private final String name;
    /**
     * 中文描述
     */
    private final String desc;

    AcceptanceVerificationType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
