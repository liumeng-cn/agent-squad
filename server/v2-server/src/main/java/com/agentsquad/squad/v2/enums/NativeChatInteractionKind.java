package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生交互类型
 * <p>
 * 在 SDK 协议边界识别交互用途，用户问题只接收外部执行类型的文本答案。
 */
@Getter
public enum NativeChatInteractionKind {
    EXTERNAL_EXECUTION("external_execution", "外部执行答复"),
    CONFIRMATION("confirmation", "工具操作审批"),
    UNKNOWN("", "未知交互");

    /**
     * SDK 协议值。
     */
    private final String value;

    /**
     * 中文含义。
     */
    private final String desc;

    NativeChatInteractionKind(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static NativeChatInteractionKind fromValue(Object value) {
        for (var kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
