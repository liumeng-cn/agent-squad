package com.agentsquad.squad.runtime;

import com.agentsquad.squad.conversation.domain.ResponseContextSnapshot;
import com.agentsquad.squad.conversation.dto.MessageReceipt;
import com.agentsquad.squad.conversation.service.ResponseContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL会话前缀并发冻结验证。
 * <p>
 * 使用独立测试数据库，确认可重复读下并发重投复用同份材料，不创建目标或模型请求。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_MESSAGE_TEST_URL", matches = ".+")
class MySqlResponseContextTest {

    @Test
    void concurrentFreezeUsesCommittedSnapshot() throws Exception {
        String owner = UUID.randomUUID().toString();
        try (ConfigurableApplicationContext context = MessageAcceptanceHttpTest.start(
                System.getenv("SQUAD_MESSAGE_TEST_URL"), System.getenv("SQUAD_MESSAGE_TEST_USER"),
                System.getenv("SQUAD_MESSAGE_TEST_PASSWORD"), owner)) {
            MessageTestActor actor = MessageAcceptanceHttpTest.actor(context);
            MessageReceipt first = MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/messages",
                    "context-first", Map.of("text", "只能读到这条"));
            MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/" + first.conversationId() + "/messages",
                    "context-later", Map.of("text", "不能提前进入前一个回复"));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id = ?",
                    String.class, first.executionId());
            ResponseContextService service = context.getBean(ResponseContextService.class);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var threads = Executors.newFixedThreadPool(2)) {
                var a = threads.submit(() -> freeze(service, owner, binding, ready, start));
                var b = threads.submit(() -> freeze(service, owner, binding, ready, start));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                ResponseContextSnapshot snapshot = a.get(15, TimeUnit.SECONDS);
                assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(snapshot);
                assertThat(snapshot.messages()).extracting(m -> m.text()).containsExactly("只能读到这条");
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task WHERE user_id = ?", Long.class, owner)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE user_id = ?", Long.class, owner)).isZero();
        }
    }

    private ResponseContextSnapshot freeze(ResponseContextService service, String owner, String binding,
                                           CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("上下文并发测试启动超时");
        }
        return service.freeze(owner, binding);
    }
}
