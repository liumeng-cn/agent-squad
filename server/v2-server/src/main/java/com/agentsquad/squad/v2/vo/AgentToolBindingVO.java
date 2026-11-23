package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

/**
 * Agent 工具绑定视图
 * <p>
 * Agent 版本绑定的工具版本及其可用状态和可升级版本。
 */
@Data
public class AgentToolBindingVO {
    /**
     * 工具版本ID
     */
    private String toolVersionId;

    /**
     * 工具业务ID
     */
    private String toolId;

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
     * 绑定的版本号
     */
    private Integer versionNo;

    /**
     * 该工具最新发布版本号
     */
    private Integer latestVersionNo;

    /**
     * 工具启用、版本已发布且依赖可用
     */
    private boolean usable;
}
