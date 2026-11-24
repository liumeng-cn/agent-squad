package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.bo.PromptSectionsBO;
import com.agentsquad.squad.v2.config.JsonbTypeHandler;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 配置版本历史
 * <p>
 * 映射 sq_agent_version，每次保存新增一条不可变配置版本，同时保存结构化提示词分块与拼装后的完整系统提示词。
 */
@Data
@TableName(value = "sq_agent_version", schema = "squad_chat", autoResultMap = true)
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
     * 所属 Agent 业务ID
     */
    private Long agentId;

    /**
     * 配置版本号
     */
    private Integer versionNo;

    /**
     * 版本就绪状态
     */
    private ConfigReadiness readiness;

    /**
     * 职责描述
     */
    private String description;

    /**
     * 结构化提示词分块
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private PromptSectionsBO promptSections;

    /**
     * 拼装后的完整系统提示词
     */
    private String systemPrompt;

    /**
     * 最大执行步数，空值使用平台默认值
     */
    private Integer maxIters;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 创建时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant createTime;

    /**
     * 更新时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant lastUpdateTime;
}
