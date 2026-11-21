package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import lombok.Data;

import java.time.Instant;

/**
 * 工具版本视图
 * <p>
 * 用于管理端查看版本配置和发布状态。
 */
@Data
public class ToolVersionVO {
    /**
     * 版本业务ID
     */
    private String toolVersionId;

    /**
     * 资源业务ID
     */
    private String toolId;

    /**
     * 顺序版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private AgentVersionStatus status;

    /**
     * 版本修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 模型工具名称
     */
    private String exposedName;

    /**
     * 原生或Java实现版本标识
     */
    private String implementationKey;

    /**
     * MCP连接版本ID
     */
    private String mcpServerVersionId;

    /**
     * 远端工具名称
     */
    private String remoteToolName;
}
