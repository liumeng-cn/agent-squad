package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 子 Agent 委派视图
 * <p>
 * 主 Agent 版本委派的子 Agent 版本及其可用状态。
 */
@Data
public class AgentDelegationVO {
    /**
     * 子 Agent 版本ID
     */
    private String subagentVersionId;

    /**
     * 子 Agent 业务ID
     */
    private String subagentId;

    /**
     * 子 Agent 编码
     */
    private String subagentCode;

    /**
     * 子 Agent 名称
     */
    private String subagentName;

    /**
     * 子 Agent 该版本的职责描述
     */
    private String description;

    /**
     * 委派的版本号
     */
    private Integer versionNo;

    /**
     * 子 Agent 当前生效版本号
     */
    private Integer currentVersionNo;

    /**
     * 委派名称
     */
    private String delegationName;

    /**
     * 委派说明
     */
    private String delegationDescription;

    /**
     * 子 Agent 启用且版本已发布
     */
    private boolean usable;
}
