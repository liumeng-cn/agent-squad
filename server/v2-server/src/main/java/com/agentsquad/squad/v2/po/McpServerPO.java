package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MCP 服务目录
 * <p>
 * 映射 sq_mcp_server，保存服务的稳定身份、说明和启停状态；连接参数保存在版本表。
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
     * 服务业务ID
     */
    private Long mcpServerId;

    /**
     * 服务编码
     */
    private String serverCode;

    /**
     * 展示名称
     */
    private String serverName;

    /**
     * 服务用途说明
     */
    private String description;

    /**
     * 启停状态
     */
    private ConfigStatus status;

    /**
     * 目录修订号
     */
    private Long revision;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 最后更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 最后更新时间，由数据库维护
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant lastUpdateTime;
}
