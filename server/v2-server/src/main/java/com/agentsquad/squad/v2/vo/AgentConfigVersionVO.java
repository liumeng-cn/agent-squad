package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Agent 版本视图
 * <p>
 * 返回一个 Agent 版本的提示词、运行参数、绑定工具与子 Agent 委派。
 */
@Data
public class AgentConfigVersionVO {
    /**
     * 版本业务ID
     */
    private String agentVersionId;

    /**
     * Agent 业务ID
     */
    private String agentId;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private VersionStatus status;

    /**
     * 版本修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;

    /**
     * 职责描述
     */
    private String description;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 最大执行步数
     */
    private Integer maxIters;

    /**
     * 绑定的工具版本
     */
    private List<AgentToolBindingVO> tools;

    /**
     * 子 Agent 委派
     */
    private List<AgentDelegationVO> subagents;
}
