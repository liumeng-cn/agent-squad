package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import lombok.Data;

/**
 * MCP连接目录视图
 * <p>
 * 用于管理端查看资源目录。
 */
@Data
public class McpServerVO {
    /**
     * 资源业务ID
     */
    private String mcpServerId;

    /**
     * 连接编码
     */
    private String serverCode;

    /**
     * 展示名称
     */
    private String serverName;

    /**
     * 启停状态
     */
    private AgentConfigStatus status;
}
