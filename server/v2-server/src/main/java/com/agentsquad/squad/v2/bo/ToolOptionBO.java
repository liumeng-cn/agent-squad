package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

/**
 * 可绑定工具选项
 * <p>
 * 仅包含未删除、启用且最新配置就绪的工具，按工具业务 ID 唯一。
 */
@Data
public class ToolOptionBO {
    private Long toolId;

    private String toolCode;

    private String toolName;

    private String displayAlias;

    private ToolType toolType;

    private Long latestVersionId;

    private Integer latestVersionNo;

    private String exposedName;

    private String description;

    private Boolean readOnly;
}
