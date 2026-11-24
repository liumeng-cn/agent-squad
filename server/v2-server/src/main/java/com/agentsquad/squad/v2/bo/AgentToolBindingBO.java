package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

/**
 * Agent 关联的工具详情
 * <p>
 * 既用于配置态展示工具最新配置版本及其可用性，也用于发布快照展示定格版本。
 */
@Data
public class AgentToolBindingBO {
    private Long toolId;

    private String toolCode;

    private String toolName;

    private String displayAlias;

    private ToolType toolType;

    private ConfigStatus toolStatus;

    private Long toolVersionId;

    private Integer versionNo;

    private ConfigReadiness readiness;

    private String exposedName;

    private String description;

    private Boolean readOnly;

    private Long mcpServerId;

    private Long mcpServerVersionId;

    private String mcpServerName;

    private ConfigStatus mcpServerStatus;

    private ConfigReadiness mcpServerReadiness;

    private boolean usable;

    private String unusableReason;
}
