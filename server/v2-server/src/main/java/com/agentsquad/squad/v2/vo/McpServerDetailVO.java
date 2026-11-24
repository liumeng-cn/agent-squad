package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * MCP 服务详情视图
 */
@Data
public class McpServerDetailVO {
    /**
     * 服务目录摘要
     */
    private McpServerVO server;

    /**
     * 最新配置版本内容
     */
    private McpServerVersionVO latestVersion;

    /**
     * 历史配置版本列表
     */
    private List<VersionSummaryVO> versions = List.of();

    /**
     * 引用该 MCP 的下游工具
     */
    private List<ConfigUsageVO> usages = List.of();
}
