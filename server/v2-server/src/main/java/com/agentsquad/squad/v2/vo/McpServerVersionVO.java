package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * MCP 配置版本详情视图
 */
@Data
public class McpServerVersionVO {
    private String mcpServerVersionId;

    private String mcpServerId;

    private Integer versionNo;

    private ConfigReadiness readiness;

    private McpTransport transportType;

    private String endpoint;

    private String credentialRef;

    private List<KeyValueVO> headers = List.of();

    private Integer timeoutMs;

    private Instant lastUpdateTime;
}
