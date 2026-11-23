package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

/**
 * 可绑定工具投影
 * <p>
 * 已发布、启用且依赖可用的工具版本。
 */
@Data
public class ToolOptionBO {
    /**
     * 工具版本ID
     */
    private Long toolVersionId;

    /**
     * 工具业务ID
     */
    private Long toolId;

    /**
     * 工具编码
     */
    private String toolCode;

    /**
     * 展示名称
     */
    private String toolName;

    /**
     * 工具类型
     */
    private ToolType toolType;

    /**
     * 模型工具名称
     */
    private String exposedName;

    /**
     * 用途说明
     */
    private String description;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 是否最新可用版本
     */
    private boolean latest;
}
