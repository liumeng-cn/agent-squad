package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 任务要求修改的处理状态
 * <p>
 * 供后台决策与页面协议共用，等待不等于拒绝或已经应用。
 */
@Getter
public enum TaskDirectiveStatus {
    ACCEPTED(1, "ACCEPTED", "已受理"),
    APPLIED(2, "APPLIED", "已应用"),
    REJECTED(3, "REJECTED", "已拒绝");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    TaskDirectiveStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
