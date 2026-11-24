package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 工具目录
 * <p>
 * 映射 sq_tool，维护工具稳定身份、展示别称、类型、最新配置版本指针、启停状态与逻辑删除标记。
 */
@Data
@TableName(value = "sq_tool", schema = "squad_chat")
public class ToolPO {
    /**
     * 存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工具业务ID
     */
    private Long toolId;

    /**
     * 系统自动生成的工具编码
     */
    private String toolCode;

    /**
     * 后台展示名称
     */
    private String toolName;

    /**
     * 聊天前端事件展示别称，留空回退工具名称
     */
    private String displayAlias;

    /**
     * 工具类型
     */
    private ToolType toolType;

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
