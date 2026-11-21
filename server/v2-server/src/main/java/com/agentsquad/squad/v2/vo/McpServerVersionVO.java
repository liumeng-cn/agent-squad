package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;

import java.time.Instant;

/**
 * MCP连接版本视图
 * <p>
 * 用于管理端查看版本配置和发布状态。
 */
@Data
public class McpServerVersionVO {
    /**
     * 版本业务ID
     */
    private String mcpServerVersionId;

    /**
     * 资源业务ID
     */
    private String mcpServerId;

    /**
     * 顺序版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private AgentVersionStatus status;

    /**
     * 版本修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 连接传输方式
     */
    private McpTransport transportType;

    /**
     * 远程连接地址
     */
    private String endpoint;

    /**
     * 连接凭据引用
     */
    private String credentialRef;
}
