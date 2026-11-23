package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * 工具详情视图
 * <p>
 * 编辑页一次加载工具摘要、版本记录和绑定它的 Agent。
 */
@Data
public class ToolDetailVO {
    /**
     * 工具摘要
     */
    private ToolVO tool;

    /**
     * 版本记录，版本号倒序
     */
    private List<VersionSummaryVO> versions;

    /**
     * 绑定该工具的 Agent 版本
     */
    private List<ConfigUsageVO> usages;
}
