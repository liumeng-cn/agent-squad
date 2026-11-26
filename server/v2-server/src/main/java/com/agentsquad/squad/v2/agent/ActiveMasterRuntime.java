package com.agentsquad.squad.v2.agent;

import com.agentsquad.squad.v2.agent.mcp.SharedMcpClientHandle;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 已激活的 Master 运行时实例包装
 * <p>
 * 封装单个 Master 当前生效 Release 的 HarnessAgent、MCP 共享连接句柄与事件别称映射，支持淘汰时统一释放资源。
 */
@Slf4j
@Getter
public class ActiveMasterRuntime implements AutoCloseable {
    private final String agentCode;

    private final String agentName;

    private final String displayName;

    private final String description;

    private final Long releaseId;

    private final Integer releaseNo;

    private final boolean standalone;

    private final boolean builtIn;

    private final HarnessAgent agent;

    private final List<SharedMcpClientHandle> mcpHandles;

    private final Map<String, String> activityAliases;

    public ActiveMasterRuntime(String agentCode, String agentName, String displayName, String description,
                               Long releaseId, Integer releaseNo, boolean standalone, boolean builtIn,
                               HarnessAgent agent, List<SharedMcpClientHandle> mcpHandles,
                               Map<String, String> activityAliases) {
        this.agentCode = agentCode;
        this.agentName = agentName;
        this.displayName = displayName;
        this.description = description;
        this.releaseId = releaseId;
        this.releaseNo = releaseNo;
        this.standalone = standalone;
        this.builtIn = builtIn;
        this.agent = agent;
        this.mcpHandles = List.copyOf(mcpHandles);
        this.activityAliases = Map.copyOf(activityAliases);
    }

    @Override
    public void close() {
        for (SharedMcpClientHandle handle : mcpHandles) {
            handle.close();
        }
        if (!builtIn && agent != null) {
            try {
                agent.close();
            } catch (RuntimeException ignored) {
                log.debug("关闭旧版 Master HarnessAgent 完成，agentCode={}，releaseNo={}", agentCode, releaseNo);
            }
        }
    }
}
