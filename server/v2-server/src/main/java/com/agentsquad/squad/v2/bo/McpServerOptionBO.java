package com.agentsquad.squad.v2.bo;

import lombok.Data;

/**
 * MCP 服务版本选项投影
 * <p>
 * 启用服务的已发布版本，供工具绑定选择。
 */
@Data
public class McpServerOptionBO {
    /**
     * 服务版本ID
     */
    private Long mcpServerVersionId;

    /**
     * 服务业务ID
     */
    private Long mcpServerId;

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
     * 是否最新发布版本
     */
    private boolean latest;
}
