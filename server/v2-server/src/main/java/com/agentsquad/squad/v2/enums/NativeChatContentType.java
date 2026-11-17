package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生公开模型内容类型
 * <p>
 * 只允许明确正文和思考文本进入聊天展示，其他模型载荷保持内部使用。
 */
@Getter
public enum NativeChatContentType {
    TEXT("text", "正文"),
    THINKING("thinking", "模型明确返回的思考文本"),
    UNKNOWN("", "不公开的内容");

    /**
     * SDK 内容块类型。
     */
    private final String value;
    /**
     * 中文说明。
     */
    private final String desc;

    NativeChatContentType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static NativeChatContentType fromValue(Object value) {
        for (var type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
