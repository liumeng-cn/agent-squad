package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 计划结果汇总阶段
 * <p>
 * 执行等待、恢复协调、效果核对与最终结论分开，不能把协调机会当作发布许可。
 */
@Getter
public enum PlanJoinStage {
    WAITING_WORK(1, "WAITING_WORK", "等待工作完成"),
    NEEDS_COORDINATION(2, "NEEDS_COORDINATION", "需要协调"),
    WAITING_EFFECTS(3, "WAITING_EFFECTS", "等待效果核对"),
    READY(4, "READY", "可汇总");

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

    PlanJoinStage(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
