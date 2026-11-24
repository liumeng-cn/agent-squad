package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * 工具详情视图
 */
@Data
public class ToolDetailVO {
    /**
     * 工具目录摘要
     */
    private ToolVO tool;

    /**
     * 最新配置版本内容
     */
    private ToolVersionVO latestVersion;

    /**
     * 历史配置版本列表
     */
    private List<VersionSummaryVO> versions = List.of();

    /**
     * 引用该工具的下游 Agent
     */
    private List<ConfigUsageVO> usages = List.of();
}
