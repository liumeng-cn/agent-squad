package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * Agent 详情视图
 */
@Data
public class AgentDetailVO {
    /**
     * Agent 目录摘要
     */
    private AgentConfigVO agent;

    /**
     * 最新配置版本内容
     */
    private AgentConfigVersionVO latestVersion;

    /**
     * 历史配置版本列表
     */
    private List<VersionSummaryVO> versions = List.of();

    /**
     * Master 发布快照历史列表（Sub 为空）
     */
    private List<AgentReleaseSummaryVO> releases = List.of();

    /**
     * 引用该 Sub 的下游 Master（Master 为空）
     */
    private List<ConfigUsageVO> usages = List.of();
}
