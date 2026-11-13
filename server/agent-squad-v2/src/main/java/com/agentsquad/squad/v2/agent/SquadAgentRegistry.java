package com.agentsquad.squad.v2.agent;

import com.agentsquad.squad.common.model.SquadAgentRef;
import com.agentsquad.squad.enums.SquadAgentType;
import com.agentsquad.squad.v2.utils.SquadIdUtils;
import io.agentscope.core.event.AgentEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 单次执行内的 Agent 实例登记表，依据原生 source 路径建立父子归属。
 */
public class SquadAgentRegistry {
    private final SquadAgentRef master;
    private final Map<String, SquadAgentRef> agentsBySource = new HashMap<>();

    public SquadAgentRegistry(Long executionId, String agentId, String agentName) {
        String instanceId = String.valueOf(executionId);
        master = new SquadAgentRef(agentId, instanceId, agentName, SquadAgentType.MASTER, null, instanceId, 0);
    }

    /**
     * 根据 AgentScope 原生 source 解析 Agent 实例。
     */
    public synchronized SquadAgentRef resolve(AgentEvent event) {
        String source = event.getSource();
        return source == null || source.isBlank() ? master : resolveSource(source, event);
    }

    private SquadAgentRef resolveSource(String source, AgentEvent event) {
        SquadAgentRef found = agentsBySource.get(source);
        if (found != null) return found;
        String[] parts = source.split("/");
        String parentSource = parts.length >= 5 ? String.join("/", Arrays.copyOf(parts, parts.length - 2)) : null;
        SquadAgentRef parent = parentSource == null ? master : resolveSource(parentSource, null);
        String agentId = event != null && event.getExecution() != null ? event.getExecution().agentId() : null;
        if (agentId == null || agentId.isBlank()) agentId = parts.length >= 2 ? parts[parts.length - 2] : source;
        String instanceId = String.valueOf(SquadIdUtils.nextSnowflakeId());
        SquadAgentRef agent = new SquadAgentRef(agentId, instanceId, agentId, SquadAgentType.SUBAGENT,
                parent.getAgentInstanceId(), master.getAgentInstanceId(), parent.getDepth() + 1);
        agentsBySource.put(source, agent);
        return agent;
    }

    public SquadAgentRef master() {
        return master;
    }
}