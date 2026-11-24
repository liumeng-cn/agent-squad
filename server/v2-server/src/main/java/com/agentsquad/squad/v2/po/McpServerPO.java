package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
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
 * 映射 sq_mcp_server，维护服务稳定身份、最新配置版本指针、启停状态和逻辑删除标记。
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
     * MCP 服务业务ID
     */
    private Long mcpServerId;

    /**
     * 系统自动生成的服务编码
     */
    private String serverCode;

    /**
     * 后台展示名称
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
     * 最新配置版本ID
     */
    private Long latestVersionId;

    /**
     * 最新配置版本号
     */
    private Integer latestVersionNo;

    /**
     * 最新配置版本就绪状态
     */
    private ConfigReadiness latestReadiness;

    /**
     * 目录并发修订号
     */
    private Long revision;

    /**
     * 逻辑删除时间，未删除为空
     */
    private Instant deletedAt;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 最后更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 最后更新时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant lastUpdateTime;
}
