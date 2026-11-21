package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import com.agentsquad.squad.v2.enums.McpTransport;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MCP连接版本
 * <p>
 * 保存可独立发布的工具或连接配置。
 */
@Data
@TableName(value = "sq_mcp_server_version", schema = "squad_chat")
public class McpServerVersionPO {
    /**
     * 存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版本业务ID
     */
    private Long mcpServerVersionId;

    /**
     * 资源业务ID
     */
    private Long mcpServerId;

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

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;
}
