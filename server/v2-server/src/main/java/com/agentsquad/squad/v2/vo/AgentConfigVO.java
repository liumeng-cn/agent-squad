package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentPublishState;
import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 目录视图
 */
@Data
public class AgentConfigVO {
    private String agentId;

    private String agentCode;

    private String agentName;

    private String displayAlias;

    private AgentType agentType;

    private ConfigStatus status;

    private Long revision;

    private String latestVersionId;

    private Integer latestVersionNo;

    private ConfigReadiness latestReadiness;

    private String description;

    private String currentReleaseId;

    private Integer currentReleaseNo;

    private Instant currentReleaseAt;

    private AgentPublishState publishState;

    private Integer toolCount;

    private Integer subagentCount;

    private Integer usageCount;

    private Instant lastUpdateTime;
}
