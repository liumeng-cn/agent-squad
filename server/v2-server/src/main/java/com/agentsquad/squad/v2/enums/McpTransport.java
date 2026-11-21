package com.agentsquad.squad.v2.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP传输方式
 * <p>
 * 对应管理配置和数据库有限取值。
 */
@Getter
@RequiredArgsConstructor
public enum McpTransport {
    STREAMABLE_HTTP("流式HTTP"),
    SSE("服务端事件流");

    /**
     * 中文描述
     */
    private final String desc;
}
