package com.agentsquad.squad.integration.agentscope.completion;

/**
 * 模型停止原因
 * <p>
 * 适配模块只认可已验证的供应商协议取值，未知原因不回退为正常停止。
 */
public enum ModelFinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    TOOL_CALLS,
    UNKNOWN;

    static ModelFinishReason fromNative(String reason) {
        return switch (reason) {
            case "stop" -> STOP;
            case "length" -> LENGTH;
            case "content_filter" -> CONTENT_FILTER;
            case "tool_calls" -> TOOL_CALLS;
            default -> UNKNOWN;
        };
    }
}
