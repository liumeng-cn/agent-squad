package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

import java.time.Instant;

/**
 * 工具摘要投影
 * <p>
 * 工具目录联查最新发布版本、草稿与生效引用数量的结果。
 */
@Data
public class ToolSummaryBO {
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
     * 启停状态
     */
    private ConfigStatus status;

    /**
     * 目录修订号
     */
    private Long revision;

    /**
     * 模型工具名称
     */
    private String exposedName;

    /**
     * 用途说明
     */
    private String description;

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
     * 生效引用的 Agent 数量
     */
    private Integer usageCount;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
