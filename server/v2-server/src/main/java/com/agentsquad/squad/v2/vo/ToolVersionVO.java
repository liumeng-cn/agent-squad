package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具版本视图
 * <p>
 * 返回一个工具版本的模型可见定义与按类型的实现配置。
 */
@Data
public class ToolVersionVO {
    /**
     * 版本业务ID
     */
    private String toolVersionId;

    /**
     * 工具业务ID
     */
    private String toolId;

    /**
     * 工具类型
     */
    private ToolType toolType;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private VersionStatus status;

    /**
     * 版本修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;

    /**
     * 模型工具名称
     */
    private String exposedName;

    /**
     * 用途说明
     */
    private String description;

    /**
     * 调用参数的 JSON Schema
     */
    private Map<String, Object> inputSchema;

    /**
     * 是否只读
     */
    private boolean readOnly;

    /**
     * 内置工具实现标识
     */
    private String implementationKey;

    /**
     * MCP 服务版本ID
     */
    private String mcpServerVersionId;

    /**
     * MCP 服务名称
     */
    private String mcpServerName;

    /**
     * MCP 服务版本号
     */
    private Integer mcpServerVersionNo;

    /**
     * MCP 服务中的工具名称
     */
    private String remoteToolName;

    /**
     * HTTP 请求模板
     */
    private HttpToolSpecVO httpSpec;
}
