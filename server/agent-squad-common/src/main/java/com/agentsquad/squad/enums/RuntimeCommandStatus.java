package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 平台持久命令的投递状态。
 * <p>
 * 状态反映平台和原生会话之间的受理进度，不代表用户目标已完成。
 */
@Getter
public enum RuntimeCommandStatus {
    PENDING(1, "PENDING", "待投递"),
    DELIVERING(2, "DELIVERING", "投递中"),
    DELIVERED(3, "DELIVERED", "已投递"),
    APPLIED(4, "APPLIED", "已应用"),
    BLOCKED(5, "BLOCKED", "已阻塞"),
    CANCELLED(6, "CANCELLED", "已取消");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    RuntimeCommandStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
