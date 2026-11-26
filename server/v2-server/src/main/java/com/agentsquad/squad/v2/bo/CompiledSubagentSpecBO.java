package com.agentsquad.squad.v2.bo;

import io.agentscope.core.tool.AgentTool;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 已定格的子 Agent 运行时装配规格
 * <p>
 * 承载单个子 Agent 在某次 Master 发布快照中的委派名称、说明、系统提示词、最大步数及专属工具实例。
 */
@Data
public class CompiledSubagentSpecBO {
    /**
     * 子 Agent 业务ID
     */
    private Long subagentId;

    /**
     * Master 调用该子 Agent 的委派名称
     */
    private String delegationName;

    /**
     * 供 Master 决策何时委派的职责说明
     */
    private String delegationDescription;

    /**
     * 前端事件流展示别称
     */
    private String displayAlias;

    /**
     * 定格的子 Agent 系统提示词
     */
    private String systemPrompt;

    /**
     * 定格的最大执行步数（可空）
     */
    private Integer maxIters;

    /**
     * 该子 Agent 绑定的专属可执行工具列表
     */
    private List<AgentTool> tools = new ArrayList<>();
}
