package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * MCP 服务详情视图
 * <p>
 * 编辑页一次加载服务摘要、版本记录和引用它的工具。
 */
@Data
public class McpServerDetailVO {
    /**
     * 服务摘要
     */
    private McpServerVO server;

    /**
     * 版本记录，版本号倒序
     */
    private List<VersionSummaryVO> versions;

    /**
     * 引用该服务的工具版本
     */
    private List<ConfigUsageVO> usages;
}
