package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 摘要投影
 * <p>
 * Agent 目录联查当前版本、草稿、绑定数量与生效委派数量的结果。
 */
@Data
public class AgentSummaryBO {
    /**
     * Agent 业务ID
     */
    private Long agentId;

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 展示名称
     */
    private String agentName;

    /**
     * Agent 类型
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
    private Long currentVersionId;

    /**
     * 当前生效版本号
     */
    private Integer currentVersionNo;

    /**
     * 草稿版本ID
     */
    private Long draftVersionId;

    /**
     * 绑定工具数量
     */
    private Integer toolCount;

    /**
     * 委派子 Agent 数量
     */
    private Integer subagentCount;

    /**
     * 生效委派它的主 Agent 数量
     */
    private Integer usageCount;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
