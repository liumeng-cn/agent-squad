package com.agentsquad.squad.v2.bo;

import lombok.Data;

/**
 * 可委派子 Agent 投影
 * <p>
 * 启用子 Agent 的已发布版本。
 */
@Data
public class SubagentOptionBO {
    /**
     * 子 Agent 版本ID
     */
    private Long subagentVersionId;

    /**
     * 子 Agent 业务ID
     */
    private Long agentId;

    /**
     * 子 Agent 编码
     */
    private String agentCode;

    /**
     * 子 Agent 名称
     */
    private String agentName;

    /**
     * 该版本的职责描述
     */
    private String description;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 是否当前生效版本
     */
    private boolean current;
}
