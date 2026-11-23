package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * MCP 服务版本选项
 * <p>
 * 工具绑定时可选的已发布服务版本，不包含连接参数。
 */
@Data
public class McpServerOptionVO {
    /**
     * 服务版本ID
     */
    private String mcpServerVersionId;

    /**
     * 服务业务ID
     */
    private String mcpServerId;

    /**
     * 服务编码
     */
    private String serverCode;

    /**
     * 服务名称
     */
    private String serverName;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 是否该服务最新发布版本
     */
    private boolean latest;
}
