package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

/**
 * Agent 关联工具视图
 */
@Data
public class AgentToolBindingVO {
    private String toolId;

    private String toolCode;

    private String toolName;

    private String displayAlias;

    private ToolType toolType;

    private ConfigStatus toolStatus;

    private String toolVersionId;

    private Integer versionNo;

    private ConfigReadiness readiness;

    private String exposedName;

    private String description;

    private Boolean readOnly;

    private String mcpServerId;

    private String mcpServerName;

    private boolean usable;

    private String unusableReason;
}
