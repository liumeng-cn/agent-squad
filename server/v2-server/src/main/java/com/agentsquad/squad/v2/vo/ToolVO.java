package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

import java.time.Instant;

/**
 * 工具列表视图
 */
@Data
public class ToolVO {
    private String toolId;

    private String toolCode;

    private String toolName;

    private String displayAlias;

    private ToolType toolType;

    private ConfigStatus status;

    private Long revision;

    private String latestVersionId;

    private Integer latestVersionNo;

    private ConfigReadiness latestReadiness;

    private String exposedName;

    private String description;

    private Boolean readOnly;

    private Integer usageCount;

    private Instant lastUpdateTime;
}
