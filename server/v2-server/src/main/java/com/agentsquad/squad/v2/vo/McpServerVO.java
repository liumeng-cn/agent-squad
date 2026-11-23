package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

import java.time.Instant;

/**
 * MCP 服务目录视图
 * <p>
 * 列表与编辑页使用的服务摘要，包含最新发布版本、草稿和生效引用数量。
 */
@Data
public class McpServerVO {
    /**
     * 服务业务ID
     */
    private String mcpServerId;

    /**
     * 服务编码
     */
    private String serverCode;

    /**
     * 展示名称
     */
    private String serverName;

    /**
     * 服务用途说明
     */
    private String description;

    /**
     * 启停状态
     */
    private ConfigStatus status;

    /**
     * 目录修订号
     */
    private Long revision;

    /**
     * 最新发布版本ID
     */
    private String publishedVersionId;

    /**
     * 最新发布版本号
     */
    private Integer publishedVersionNo;

    /**
     * 草稿版本ID
     */
    private String draftVersionId;

    /**
     * 生效引用的工具数量
     */
    private Integer usageCount;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
