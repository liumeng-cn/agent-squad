package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生聊天协议取值
 * <p>
 * 集中转换固定 SDK 的协议字符串，未知取值不进入公开业务分支。
 */
@Getter
public enum NativeChatStatus {
    COMPLETED("completed", "已完成"),
    SUSPENDED("suspended", "等待交互"),
    INTERRUPTED("interrupted", "已中断"),
    CANCELLED("cancelled", "已取消"),
    FAILED("failed", "执行失败"),
    UNKNOWN("unknown", "未知状态");
    /**
     * SDK 协议值。
     */
    private final String value;
    /**
     * 中文含义。
     */
    private final String desc;

    NativeChatStatus(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static NativeChatStatus fromValue(Object value) {
        for (var item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return UNKNOWN;
    }
}
