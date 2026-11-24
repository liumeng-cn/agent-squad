package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

/**
 * Master 委派的 Sub 详情
 * <p>
 * 既用于配置态展示子 Agent 最新配置版本及其可用性，也用于发布快照展示定格版本。
 */
@Data
public class AgentDelegationBO {
    private Long subagentId;

    private String subagentCode;

    private String subagentName;

    private String subagentDisplayAlias;

    private AgentType subagentType;

    private ConfigStatus subagentStatus;

    private Long subagentVersionId;

    private Integer versionNo;

    private ConfigReadiness readiness;

    private String delegationName;

    private String delegationDescription;

    private String displayAlias;

    private String subagentDescription;

    private Integer toolCount;

    private boolean usable;

    private String unusableReason;
}
