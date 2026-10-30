package com.agentsquad.squad.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.InMemorySessionLogStore;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLog;
import io.agentscope.core.session.SessionLogException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 固定 AgentScope SDK 的原生日志 writer fence 语义，作为 A05 的第二道并发屏障证据。
 * 平台业务 guard 的跨实例竞争由 WorkerGuardIntegrationTest 验证。
 */
class NativeWriterFencingTest {

    @Test
    void staleWriterCannotRenewAfterAnotherWriterTakesOver() throws Exception {
        InMemorySessionLogStore store = new InMemorySessionLogStore();
        RuntimeContext context = RuntimeContext.builder()
                .userId("user-1").sessionId("session-1").build();
        SessionKey key = new SessionKey("user-1", "worker-1", "session-1");
        SessionLog first = store.open(key, context);
        SessionLog second = store.open(key, context);

        SessionLog.Writer oldWriter = first.acquire("old-instance", Duration.ofMillis(500));
        assertThatThrownBy(() -> second.acquire("new-instance", Duration.ofSeconds(1)))
                .isInstanceOf(SessionLogException.class);
        Thread.sleep(550);
        second.acquire("new-instance", Duration.ofSeconds(1));
        assertThatThrownBy(() -> first.renew(oldWriter, Duration.ofSeconds(1)))
                .isInstanceOf(SessionLogException.class)
                .hasMessageContaining("fenced");
    }
}
