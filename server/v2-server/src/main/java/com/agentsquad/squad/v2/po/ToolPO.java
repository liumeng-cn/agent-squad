package com.agentsquad.squad.v2.po;

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
 * 映射 sq_tool，保存工具的稳定身份、类型和启停状态；模型可见定义与实现配置保存在版本表。
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
     * 工具编码
     */
    private String toolCode;

    /**
     * 展示名称
     */
    private String toolName;

    /**
     * 工具类型
     */
    private ToolType toolType;

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
