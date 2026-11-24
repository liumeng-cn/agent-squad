package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;

import java.time.Instant;

/**
 * MCP 服务列表摘要
 */
@Data
public class McpServerSummaryBO {
    private Long mcpServerId;

    private String serverCode;

    private String serverName;

    private String description;

    private ConfigStatus status;

    private Long revision;

    private Long latestVersionId;

    private Integer latestVersionNo;

    private ConfigReadiness latestReadiness;

    private McpTransport transportType;

    private String endpoint;

    private Integer usageCount;

    private Instant lastUpdateTime;
}
