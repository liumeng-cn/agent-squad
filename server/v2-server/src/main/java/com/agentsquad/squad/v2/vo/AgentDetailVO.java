package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * Agent 详情视图
 * <p>
 * 编辑页一次加载 Agent 摘要、版本记录和委派它的主 Agent。
 */
@Data
public class AgentDetailVO {
    /**
     * Agent 摘要
     */
    private AgentConfigVO agent;

    /**
     * 版本记录，版本号倒序
     */
    private List<VersionSummaryVO> versions;

    /**
     * 委派该 Agent 的主 Agent 版本
     */
    private List<ConfigUsageVO> usages;
}
