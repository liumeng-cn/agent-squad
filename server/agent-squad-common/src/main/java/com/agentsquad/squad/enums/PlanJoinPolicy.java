package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 计划结果汇总策略
 * <p>
 * 两种策略均等待全部工作收尾；尽力交付不代表可以省略必要工作或验收。
 */
@Getter
public enum PlanJoinPolicy {
    ALL_REQUIRED(1, "ALL_REQUIRED", "全部必需完成"),
    BEST_EFFORT(2, "BEST_EFFORT", "尽力交付");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    PlanJoinPolicy(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
