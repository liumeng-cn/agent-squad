package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.time.Instant;

/**
 * 发布快照内的工具与 MCP 只读详情视图
 * <p>
 * 沿指定发布快照上下文读取定格的工具版本及对应的 MCP 版本。
 */
@Data
public class AgentReleaseToolDetailVO {
    private String releaseId;

    private Integer releaseNo;

    private Instant publishedAt;

    private String toolId;

    private String toolCode;

    private String toolName;

    private String displayAlias;

    private ToolVersionVO toolVersion;

    private McpServerVersionVO mcpVersion;

    private String mcpServerName;
}
