package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * MCP 传输方式
 * <p>
 * 只支持远程 HTTP 传输，不在服务端启动本地进程。
 */
@Getter
public enum McpTransport {
    STREAMABLE_HTTP("流式 HTTP"),
    SSE("服务端事件流");

    private final String desc;

    McpTransport(String desc) {
        this.desc = desc;
    }
}
