package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具配置版本详情视图
 */
@Data
public class ToolVersionVO {
    private String toolVersionId;

    private String toolId;

    private ToolType toolType;

    private Integer versionNo;

    private ConfigReadiness readiness;

    private String exposedName;

    private String description;

    private Map<String, Object> inputSchema;

    private Boolean readOnly;

    private String implementationKey;

    private String mcpServerId;

    private String mcpServerName;

    private String remoteToolName;

    private HttpToolSpecVO httpSpec;

    private Instant lastUpdateTime;
}
