package com.agentsquad.squad.v2.bo;

import lombok.Data;

/**
 * 可委派子 Agent 选项
 * <p>
 * 仅包含未删除、启用且最新配置就绪的子 Agent，按业务 ID 唯一。
 */
@Data
public class SubagentOptionBO {
    private Long agentId;

    private String agentCode;

    private String agentName;

    private String displayAlias;

    private Long latestVersionId;

    private Integer latestVersionNo;

    private String description;

    private Integer toolCount;
}
