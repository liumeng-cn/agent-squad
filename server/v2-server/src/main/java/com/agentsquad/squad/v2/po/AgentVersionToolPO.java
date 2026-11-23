package com.agentsquad.squad.v2.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 工具绑定
 * <p>
 * 映射管理模块的 sq_agent_version_tool 表。
 */
@Data
@TableName(value = "sq_agent_version_tool", schema = "squad_chat")
public class AgentVersionToolPO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent版本ID
     */
    private Long agentVersionId;

    /**
     * 工具版本ID
     */
    private Long toolVersionId;

    /**
     * 创建人
     */
    private Long creatorId;

    /**
     * 更新人
     */
    private Long lastUpdaterId;

    /**
     * 更新时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant lastUpdateTime;

}
