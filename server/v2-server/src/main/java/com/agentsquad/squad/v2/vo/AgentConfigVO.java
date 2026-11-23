package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 目录视图
 * <p>
 * 列表与编辑页使用的 Agent 摘要；职责描述和绑定数量取当前生效版本，尚未发布时取草稿。
 */
@Data
public class AgentConfigVO {
    /**
     * Agent 业务ID
     */
    private String agentId;

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 展示名称
     */
    private String agentName;

    /**
     * 主 Agent 或子 Agent
     */
    private AgentType agentType;

    /**
     * 启停状态
     */
    private ConfigStatus status;

    /**
     * 目录修订号
     */
    private Long revision;

    /**
     * 职责描述
     */
    private String description;

    /**
     * 当前生效版本ID
     */
    private String currentVersionId;

    /**
     * 当前生效版本号
     */
    private Integer currentVersionNo;

    /**
     * 草稿版本ID
     */
    private String draftVersionId;

    /**
     * 绑定工具数量
     */
    private Integer toolCount;

    /**
     * 委派子 Agent 数量
     */
    private Integer subagentCount;

    /**
     * 生效委派该 Agent 的主 Agent 数量
     */
    private Integer usageCount;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
