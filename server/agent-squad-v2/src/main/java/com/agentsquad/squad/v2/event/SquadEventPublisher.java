package com.agentsquad.squad.v2.event;

import com.agentsquad.squad.common.model.SquadExecutionRef;
import com.agentsquad.squad.common.model.SquadMsg;
import com.agentsquad.squad.enums.SquadEventCategory;
import com.agentsquad.squad.v2.agent.SquadAgentRegistry;
import com.agentsquad.squad.v2.bo.SquadExecutionBO;
import com.agentsquad.squad.v2.stream.AgentScopeEventAdapter;
import com.agentsquad.squad.v2.utils.SquadIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEvent;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/** V2 事件发布器，保留原生事件，仅补充平台生命周期事件。 */
@Component
public class SquadEventPublisher {
    @Resource private AgentScopeEventAdapter adapter;
    @Resource private ObjectMapper objectMapper;
    /** 转换并发布原生 AgentScope 事件。 */
    public void publishNative(SquadExecutionBO execution, AgentEvent event) {
        SquadMsg msg = adapter.adapt(event, String.valueOf(execution.getConversationId()),
            execution.nextSequence(), objectMapper);
        msg.setAgent(execution.getAgentRegistry().resolve(event));
        execution.getEventBuffer().publish(msg);
    }
    public void publishTurnStarted(SquadExecutionBO execution) {
        publishPlatform(execution, "squad.turn.started", SquadEventCategory.LIFECYCLE,
            Map.of("executionId", String.valueOf(execution.getExecutionId()),
                "conversationId", String.valueOf(execution.getConversationId())));
    }
    public void publishTurnCompleted(SquadExecutionBO execution) {
        publishPlatform(execution, "squad.turn.completed", SquadEventCategory.LIFECYCLE,
            Map.of("durationMillis", execution.getDurationMillis()));
    }
    public void publishTurnFailed(SquadExecutionBO execution, Throwable error) {
        publishPlatform(execution, "squad.turn.failed", SquadEventCategory.ERROR,
            Map.of("error", "智能体执行失败，请检查服务端日志",
                "errorType", error == null ? "UnknownError" : error.getClass().getSimpleName()));
    }
    public void publishTurnCancelled(SquadExecutionBO execution) {
        publishPlatform(execution, "squad.turn.cancelled", SquadEventCategory.CONTROL,
            Map.of("reason", "执行已取消"));
    }
    private void publishPlatform(SquadExecutionBO execution, String type,
        SquadEventCategory category, Map<String, Object> payload) {
        SquadAgentRegistry registry = execution.getAgentRegistry();
        SquadMsg msg = new SquadMsg();
        msg.setProtocolVersion("1");
        msg.setEventId(SquadIdUtils.uuidWithoutHyphens());
        msg.setSequence(execution.nextSequence());
        msg.setEventType(type);
        msg.setCategory(category);
        msg.setConversationId(String.valueOf(execution.getConversationId()));
        msg.setTurnId(null);
        msg.setAgent(registry.master());
        msg.setNativeExecution(new SquadExecutionRef(registry.master().getAgentId(),
            execution.getRuntimeContext().getSessionId(), null, execution.getRunId()));
        msg.setNativeSource(null);
        msg.setRelation(null);
        msg.setPayload(payload);
        msg.setTimestamp(Instant.now());
        execution.getEventBuffer().publish(msg);
    }
}