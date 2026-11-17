package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生聊天协议取值
 * <p>
 * 集中转换固定 SDK 的协议字符串，未知取值不进入公开业务分支。
 */
@Getter
public enum NativeChatCommandType {
    SUBMIT("submit", "新任务"),
    STEER("steer", "补充要求"),
    INJECT("inject", "注入上下文"),
    RESUME("resume", "继续任务"),
    RESPOND("respond", "交互答复"),
    INTERRUPT("interrupt", "停止执行"),
    UNKNOWN("unknown", "未知命令");
    /**
     * SDK 协议值。
     */
    private final String value;
    /**
     * 中文含义。
     */
    private final String desc;

    NativeChatCommandType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static NativeChatCommandType fromValue(Object value) {
        for (var item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return UNKNOWN;
    }
}
