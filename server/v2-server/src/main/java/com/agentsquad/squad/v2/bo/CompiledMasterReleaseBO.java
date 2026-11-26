package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.agent.mcp.SharedMcpClientHandle;
import io.agentscope.core.tool.AgentTool;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已定格的 Master 发布快照编译结果
 * <p>
 * 由 sq_agent_release 及其关联子表一次性装载生成，供 ChatAgentFactory 组装不可变的 HarnessAgent 实例。
 */
@Data
public class CompiledMasterReleaseBO {
    /**
     * Master 业务ID
     */
    private Long agentId;

    /**
     * Master 逻辑编码（跨版本固定，用作原生存储路径标识）
     */
    private String agentCode;

    /**
     * Master 名称
     */
    private String agentName;

    /**
     * 前端展示名称（优先取 displayAlias）
     */
    private String displayName;

    /**
     * 职责说明
     */
    private String description;

    /**
     * 定格发布ID
     */
    private Long releaseId;

    /**
     * 定格发布序号
     */
    private Integer releaseNo;

    /**
     * 定格的完整系统提示词
     */
    private String systemPrompt;

    /**
     * 定格的最大执行步数（可空）
     */
    private Integer maxIters;

    /**
     * Master 直接挂载的可执行工具
     */
    private List<AgentTool> directTools = new ArrayList<>();

    /**
     * Master 委派的子 Agent 规格列表
     */
    private List<CompiledSubagentSpecBO> subagents = new ArrayList<>();

    /**
     * 本快照持有的共享 MCP 连接句柄，快照淘汰时统一关闭
     */
    private List<SharedMcpClientHandle> mcpHandles = new ArrayList<>();

    /**
     * 工具暴露名或子 Agent 委派名到前端展示别称的映射
     */
    private Map<String, String> activityAliases = new LinkedHashMap<>();
}
