package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.VersionStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Agent版本
 * <p>
 * 映射管理模块的 sq_agent_version 表。
 */
@Data
@TableName(value = "sq_agent_version", schema = "squad_chat")
public class AgentVersionPO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版本业务ID
     */
    private Long agentVersionId;

    /**
     * 所属Agent
     */
    private Long agentId;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private VersionStatus status;

    /**
     * 业务提示词
     */
    private String systemPrompt;

    /**
     * 职责描述
     */
    private String description;

    /**
     * 最大步数
     */
    private Integer maxIters;

    /**
     * 编辑修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

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
