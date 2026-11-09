package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 模型停止原因
 * <p>
 * 适配模块只认可已验证的供应商协议取值，未知原因不回退为正常停止。
 */
@Getter
public enum ModelFinishReason {
    STOP(1, "STOP", "正常停止"),
    LENGTH(2, "LENGTH", "长度限制"),
    CONTENT_FILTER(3, "CONTENT_FILTER", "内容过滤"),
    TOOL_CALLS(4, "TOOL_CALLS", "工具调用"),
    UNKNOWN(5, "UNKNOWN", "未知");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ModelFinishReason(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public static ModelFinishReason fromNative(String reason) {
        return switch (reason) {
            case "stop" -> STOP;
            case "length" -> LENGTH;
            case "content_filter" -> CONTENT_FILTER;
            case "tool_calls" -> TOOL_CALLS;
            default -> UNKNOWN;
        };
    }
}
