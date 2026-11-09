package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 任务要求修改尚未应用的等待原因
 * <p>
 * 供后台决策与页面协议共用，等待不等于拒绝或已经应用。
 */
@Getter
public enum TaskDirectiveWaitReason {
    NONE(1, "NONE", "无需等待"),
    WAITING_SAFE_POINT(2, "WAITING_SAFE_POINT", "等待安全点"),
    WAITING_OPERATION(3, "WAITING_OPERATION", "等待外部操作"),
    WAITING_USER(4, "WAITING_USER", "等待用户"),
    WAITING_COORDINATOR(5, "WAITING_COORDINATOR", "等待协调者"),
    COORDINATING(6, "COORDINATING", "协调中");

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

    TaskDirectiveWaitReason(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
