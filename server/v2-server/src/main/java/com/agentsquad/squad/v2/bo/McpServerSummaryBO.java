package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

import java.time.Instant;

/**
 * MCP 服务摘要投影
 * <p>
 * 服务目录联查最新发布版本、草稿与生效引用数量的结果。
 */
@Data
public class McpServerSummaryBO {
    /**
     * 服务业务ID
     */
    private Long mcpServerId;

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
    private Long publishedVersionId;

    /**
     * 最新发布版本号
     */
    private Integer publishedVersionNo;

    /**
     * 草稿版本ID
     */
    private Long draftVersionId;

    /**
     * 生效引用的工具数量
     */
    private Integer usageCount;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
