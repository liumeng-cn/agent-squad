package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 发布快照内的 Sub 只读详情视图
 * <p>
 * 沿指定发布快照上下文读取定格的 Sub 配置版本及其工具快照。
 */
@Data
public class AgentReleaseSubagentDetailVO {
    private String releaseId;

    private Integer releaseNo;

    private Instant publishedAt;

    private String subagentId;

    private String subagentCode;

    private String subagentName;

    private String displayAlias;

    private String delegationName;

    private String delegationDescription;

    private String subagentVersionId;

    private Integer versionNo;

    private String description;

    private PromptSectionsVO promptSections = new PromptSectionsVO();

    private String systemPrompt;

    private Integer maxIters;

    private List<AgentToolBindingVO> tools = List.of();
}
