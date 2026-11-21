package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import lombok.Data;

/**
 * Agent配置视图
 * <p>
 * 用于管理端读取 Agent 目录、版本或委派关系。
 */
@Data
public class AgentConfigVO {
    /**
     * Agent业务ID
     */
    private String agentId;

    /**
     * 能力编码
     */
    private String agentCode;

    /**
     * 展示名称
     */
    private String agentName;

    /**
     * 主子类型
     */
    private AgentType agentType;

    /**
     * 启停状态
     */
    private AgentConfigStatus status;

    /**
     * 生效版本ID
     */
    private String currentVersionId;

    /**
     * 目录修订号
     */
    private Long revision;

}
