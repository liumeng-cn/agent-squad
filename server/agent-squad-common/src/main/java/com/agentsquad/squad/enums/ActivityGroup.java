package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 执行活动业务分组
 * <p>
 * 用于持久活动与页面契约，不替代回复、目标或Worker的业务完成状态。
 */
@Getter
public enum ActivityGroup {
    PREPARATION(1, "PREPARATION", "准备"),
    PLANNING(2, "PLANNING", "规划"),
    EXECUTION(3, "EXECUTION", "执行"),
    VERIFICATION(4, "VERIFICATION", "验证"),
    DELIVERY(5, "DELIVERY", "交付");

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

    ActivityGroup(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
