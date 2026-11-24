package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;

/**
 * 可选 MCP 服务视图
 */
@Data
public class McpServerOptionVO {
    private String mcpServerId;

    private String serverCode;

    private String serverName;

    private String description;

    private String latestVersionId;

    private Integer latestVersionNo;

    private McpTransport transportType;

    private String endpoint;
}
