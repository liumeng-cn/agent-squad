package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * MCP版本选项
 * <p>
 * 工具绑定时展示可用连接版本，不包含连接参数。
 */
@Data
public class McpServerVersionOptionVO {
    /**
     * MCP连接版本ID
     */
    private String mcpServerVersionId;

    /**
     * 连接名称
     */
    private String serverName;

    /**
     * 顺序版本号
     */
    private Integer versionNo;
}
