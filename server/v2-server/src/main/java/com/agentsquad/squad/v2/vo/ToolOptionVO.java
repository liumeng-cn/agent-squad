package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

/**
 * 可绑定工具选项视图
 */
@Data
public class ToolOptionVO {
    private String toolId;

    private String toolCode;

    private String toolName;

    private String displayAlias;

    private ToolType toolType;

    private String latestVersionId;

    private Integer latestVersionNo;

    private String exposedName;

    private String description;

    private Boolean readOnly;
}
