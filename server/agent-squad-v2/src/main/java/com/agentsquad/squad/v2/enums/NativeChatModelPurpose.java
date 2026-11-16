package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生聊天协议取值
 * <p>
 * 集中转换固定 SDK 的协议字符串，未知取值不进入公开业务分支。
 */
@Getter
public enum NativeChatModelPurpose {
    REASONING("REASONING", "主执行模型调用"),
    UNKNOWN("", "辅助或未知调用");
    /**
     * SDK 协议值。
     */
    private final String value;
    /**
     * 中文含义。
     */
    private final String desc;

    NativeChatModelPurpose(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static NativeChatModelPurpose fromValue(Object value) {
        for (var item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return UNKNOWN;
    }
}
