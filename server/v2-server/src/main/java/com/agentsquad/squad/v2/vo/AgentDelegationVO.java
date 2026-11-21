package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * Agent配置视图
 * <p>
 * 用于管理端读取 Agent 目录、版本或委派关系。
 */
@Data
public class AgentDelegationVO {
    /**
     * 子 Agent 版本ID
     */
    private String subagentVersionId;

    /**
     * 委派名称
     */
    private String delegationName;

    /**
     * 委派描述
     */
    private String delegationDescription;

}
