package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

import java.time.Instant;

/**
 * 工具列表摘要
 */
@Data
public class ToolSummaryBO {
    private Long toolId;

    private String toolCode;

    private String toolName;

    private String displayAlias;

    private ToolType toolType;

    private ConfigStatus status;

    private Long revision;

    private Long latestVersionId;

    private Integer latestVersionNo;

    private ConfigReadiness latestReadiness;

    private String exposedName;

    private String description;

    private Boolean readOnly;

    private Integer usageCount;

    private Instant lastUpdateTime;
}
