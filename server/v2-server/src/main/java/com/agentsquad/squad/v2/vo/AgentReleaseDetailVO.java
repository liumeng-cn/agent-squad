package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Master 发布快照详情视图
 * <p>
 * 只读展示当次发布定格的 Master 配置版本、直接工具与 Sub 委派清单。
 */
@Data
public class AgentReleaseDetailVO {
    private String releaseId;

    private Integer releaseNo;

    private String masterAgentId;

    private String masterName;

    private String masterDisplayAlias;

    private String masterVersionId;

    private Integer masterVersionNo;

    private String description;

    private PromptSectionsVO promptSections = new PromptSectionsVO();

    private String systemPrompt;

    private Integer maxIters;

    private String dependencyDigest;

    private Instant publishedAt;

    private boolean current;

    private List<AgentToolBindingVO> tools = List.of();

    private List<AgentDelegationVO> subagents = List.of();
}
