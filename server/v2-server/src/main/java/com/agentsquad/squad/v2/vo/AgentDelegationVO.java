package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

/**
 * Master 委派 Sub 视图
 */
@Data
public class AgentDelegationVO {
    private String subagentId;

    private String subagentCode;

    private String subagentName;

    private String subagentDisplayAlias;

    private ConfigStatus subagentStatus;

    private String subagentVersionId;

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
