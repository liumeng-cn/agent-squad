package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 可委派子 Agent 选项视图
 */
@Data
public class SubagentOptionVO {
    private String agentId;

    private String agentCode;

    private String agentName;

    private String displayAlias;

    private String latestVersionId;

    private Integer latestVersionNo;

    private String description;

    private Integer toolCount;
}
