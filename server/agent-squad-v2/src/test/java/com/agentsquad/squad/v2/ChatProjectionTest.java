package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.converter.ChatSessionConverter;
import com.agentsquad.squad.v2.enums.ChatExecutionStatus;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.InMemorySessionLogStore;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.session.AgentSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回复段恢复投影验证
 * <p>
 * 使用原生内存日志验证恢复执行不覆盖旧正文、旧终态和结束时间，不调用模型或工具。
 */
class ChatProjectionTest {
    @Test
    void resumedRunKeepsInterruptedReplyAndAddsIndependentReply() {
        var context = RuntimeContext.builder().userId("1").sessionId("session").build();
        var log = new InMemorySessionLogStore().open(new SessionKey("1", "agent", "session"), context);
        var writer = log.acquire("fixture-writer", Duration.ofMinutes(1));
        log.commit(writer, "fixture-batch", 0, List.of(
                event(1, "run/start", "first-run", Map.of()),
                event(2, "request/prepared", "first-run", Map.of("purpose", "REASONING", "modelCallId", "first-model")),
                event(3, "model/chunk", "first-run", chunk("first-model", "已显示的正文")),
                event(4, "run/end", "first-run", Map.of("status", "interrupted")),
                event(5, "run/start", "second-run", Map.of()),
                event(6, "request/prepared", "second-run", Map.of("purpose", "REASONING", "modelCallId", "second-model")),
                event(7, "model/chunk", "second-run", chunk("second-model", "恢复后的正文")),
                event(8, "run/end", "second-run", Map.of("status", "completed"))));
        log.release(writer);
        var session = mock(AgentSession.class);
        when(session.log()).thenReturn(log);
        var snapshot = new ChatSessionConverter().snapshot(session, "session");
        assertThat(snapshot.getMessages()).hasSize(2);
        assertThat(snapshot.getMessages().getFirst().getContent()).isEqualTo("已显示的正文");
        assertThat(snapshot.getMessages().getFirst().getStatus()).isEqualTo(ChatExecutionStatus.INTERRUPTED);
        assertThat(snapshot.getMessages().getFirst().getFinishedAt()).isEqualTo(4000L);
        assertThat(snapshot.getMessages().getLast().getContent()).isEqualTo("恢复后的正文");
        assertThat(snapshot.getMessages().getLast().getStatus()).isEqualTo(ChatExecutionStatus.COMPLETED);
        assertThat(snapshot.getMessages().getLast().getFinishedAt()).isEqualTo(8000L);
        assertThat(snapshot.getMessages().getFirst().getId()).isNotEqualTo(snapshot.getMessages().getLast().getId());
    }

    private SessionEvent event(long sequence, String type, String runId, Map<String, Object> payload) {
        return new SessionEvent(1, "event-" + sequence, sequence, sequence * 1000, type,
                runId, "turn", true, JsonUtils.getJsonCodec().toJson(payload));
    }

    private Map<String, Object> chunk(String callId, String text) {
        return Map.of("purpose", "REASONING", "modelCallId", callId,
                "chunk", Map.of("content", List.of(Map.of("type", "text", "text", text))));
    }
}
