package com.agentsquad.squad.runtime;

import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.presentation.dto.ConversationEvent;
import com.agentsquad.squad.presentation.service.ConversationQueryService;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多副本会话 SSE 跨实例水位一致性验证（D6 1.1）。
 * <p>
 * 两个 Spring 实例共享同一 MySQL：一实例写入推进会话水位，另一实例从旧水位续读只看到新事件；
 * 两实例读同一会话得到相同事件集合与相同水位，无串消息、重复或缺失。guard/租约不双主与旧 writer
 * 拒绝已由 D3 的 WorkerGuardIntegrationTest 与 NativeWriterFencingTest 覆盖，此处聚焦 SSE 水位跨实例一致。
 */
class ConversationSseCrossInstanceIntegrationTest {

    @Test
    void sseWatermarkIsConsistentAcrossInstances(@TempDir Path directory) {
        String url = MySqlTestDatabase.url(directory.resolve("sse-cross-instance"));
        String username = MySqlTestDatabase.username();
        String password = MySqlTestDatabase.password();
        String user = UUID.randomUUID().toString();

        try (ConfigurableApplicationContext instanceA = MessageAcceptanceHttpTest.start(url, username, password, user);
             ConfigurableApplicationContext instanceB = MessageAcceptanceHttpTest.start(url, username, password, user)) {
            var writes = instanceA.getBean(MessageAcceptanceService.class);
            var first = writes.accept(user, null, "first", new MessageCreateRequest("第一条", List.of()));
            writes.accept(user, first.conversationId(), "second", new MessageCreateRequest("第二条", List.of()));

            var readsB = instanceB.getBean(ConversationQueryService.class);
            var readsA = instanceA.getBean(ConversationQueryService.class);

            var eventsFromB = readsB.events(user, first.conversationId(), null);
            var eventsFromA = readsA.events(user, first.conversationId(), null);
            assertThat(eventsFromB).isNotEmpty();
            assertThat(eventsFromA).hasSameSizeAs(eventsFromB);

            long watermarkSequence = eventsFromB.getLast().sequence();
            String watermark = readsB.eventCursor(eventsFromB.getLast());

            writes.accept(user, first.conversationId(), "third", new MessageCreateRequest("第三条", List.of()));

            var afterWatermark = readsB.events(user, first.conversationId(), watermark);
            assertThat(afterWatermark).isNotEmpty();
            assertThat(afterWatermark.getFirst().sequence()).isGreaterThan(watermarkSequence);

            var allFromB = readsB.events(user, first.conversationId(), null);
            var allFromA = readsA.events(user, first.conversationId(), null);
            assertThat(allFromB).hasSize(eventsFromB.size() + afterWatermark.size());
            assertThat(allFromA).hasSameSizeAs(allFromB);

            for (int i = 1; i < allFromB.size(); i++) {
                assertThat(allFromB.get(i).sequence()).isGreaterThan(allFromB.get(i - 1).sequence());
            }
        }
    }
}
