package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

/**
 * Agent 工具绑定投影
 * <p>
 * 工具版本及其可用状态；usable 为工具启用、版本已发布且 MCP 依赖可用。
 */
@Data
public class AgentToolBindingBO {
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
     * 工具启停状态
     */
    private ConfigStatus toolStatus;

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
     * 版本状态
     */
    private VersionStatus versionStatus;

    /**
     * 最新发布版本号
     */
    private Integer latestVersionNo;

    /**
     * MCP 工具依赖的服务业务ID
     */
    private Long mcpServerId;

    /**
     * 是否可用
     */
    private boolean usable;
}
