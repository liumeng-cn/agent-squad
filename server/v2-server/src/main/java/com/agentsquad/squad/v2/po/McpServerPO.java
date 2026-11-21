package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * MCP连接目录
 * <p>
 * 保存稳定身份与管理状态。
 */
@Data
@TableName(value = "sq_mcp_server", schema = "squad_chat")
public class McpServerPO {
    /**
     * 存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 资源业务ID
     */
    private Long mcpServerId;

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

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;
}
