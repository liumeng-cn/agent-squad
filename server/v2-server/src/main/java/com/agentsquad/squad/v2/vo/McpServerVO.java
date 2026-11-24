package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;

import java.time.Instant;

/**
 * MCP 服务列表视图
 */
@Data
public class McpServerVO {
    private String mcpServerId;

    private String serverCode;

    private String serverName;

    private String description;

    private ConfigStatus status;

    private Long revision;

    private String latestVersionId;

    private Integer latestVersionNo;

    private ConfigReadiness latestReadiness;

    private McpTransport transportType;

    private String endpoint;

    private Integer usageCount;

    private Instant lastUpdateTime;
}
