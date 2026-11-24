package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;

/**
 * 可选 MCP 服务
 * <p>
 * 仅包含未删除、启用且最新配置就绪的 MCP 服务，按业务 ID 唯一。
 */
@Data
public class McpServerOptionBO {
    private Long mcpServerId;

    private String serverCode;

    private String serverName;

    private String description;

    private Long latestVersionId;

    private Integer latestVersionNo;

    private McpTransport transportType;

    private String endpoint;
}
