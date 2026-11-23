package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

import java.time.Instant;

/**
 * 工具目录视图
 * <p>
 * 列表与编辑页使用的工具摘要；工具名称和说明取最新发布版本，尚未发布时取草稿。
 */
@Data
public class ToolVO {
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
     * 生效引用的 Agent 数量
     */
    private Integer usageCount;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
