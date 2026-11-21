package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Agent配置视图
 * <p>
 * 用于管理端读取 Agent 目录、版本或委派关系。
 */
@Data
public class AgentConfigVersionVO {
    /**
     * 版本ID
     */
    private String agentVersionId;

    /**
     * 所属Agent
     */
    private String agentId;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private AgentVersionStatus status;

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
     * 草稿修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 工具版本列表
     */
    private List<String> toolVersionIds;

    /**
     * 委派列表
     */
    private List<AgentDelegationVO> subagents;

}
