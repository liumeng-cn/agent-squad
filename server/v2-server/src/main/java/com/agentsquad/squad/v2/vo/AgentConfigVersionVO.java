package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Agent 配置版本详情视图
 */
@Data
public class AgentConfigVersionVO {
    private String agentVersionId;

    private String agentId;

    private Integer versionNo;

    private ConfigReadiness readiness;

    private String description;

    private PromptSectionsVO promptSections = new PromptSectionsVO();

    private String systemPrompt;

    private Integer maxIters;

    private Instant lastUpdateTime;

    private List<AgentToolBindingVO> tools = List.of();

    private List<AgentDelegationVO> subagents = List.of();
}
