package com.agentsquad.squad.v2.stream;

import com.agentsquad.squad.enums.SquadEventCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.ExecutionIdentity;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证原生事件适配时不改名、不丢执行身份和原始载荷。 */
class AgentScopeEventAdapterTest {

    private final AgentScopeEventAdapter adapter = new AgentScopeEventAdapter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepNativeEventTypePayloadAndExecutionIdentity() {
        ExecutionIdentity identity = new ExecutionIdentity(
                "master", "session-1", "turn-1", "run-1");
        AgentEvent nativeEvent = new TextBlockDeltaEvent("reply-1", "block-1", "hello")
                .withExecution(identity);

        var message = adapter.adapt(nativeEvent, "conversation-1", 7L, objectMapper);

        assertEquals("TEXT_BLOCK_DELTA", message.getEventType());
        assertEquals(SquadEventCategory.MESSAGE, message.getCategory());
        assertEquals(nativeEvent.getId(), message.getEventId());
        assertEquals("conversation-1", message.getConversationId());
        assertEquals("turn-1", message.getTurnId());
        assertNotNull(message.getNativeExecution());
        assertEquals("master", message.getNativeExecution().getAgentId());
        assertEquals("session-1", message.getNativeExecution().getSessionId());
        assertEquals("turn-1", message.getNativeExecution().getTurnId());
        assertEquals("run-1", message.getNativeExecution().getRunId());
        assertEquals("hello", message.getPayload().get("delta"));
    }

    @Test
    void shouldClassifyKnownNativeTypeWithoutRenaming() {
        AgentEvent nativeEvent = new TextBlockDeltaEvent("reply-1", "block-1", "hello");

        var message = adapter.adapt(nativeEvent, "conversation-1", 1L, objectMapper);

        // 类型仍使用 SDK 原生名称；分类仅用于前端展示。
        assertEquals("TEXT_BLOCK_DELTA", message.getEventType());
        assertEquals(SquadEventCategory.MESSAGE, message.getCategory());
    }
}
