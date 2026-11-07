package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.domain.request.MessageCreateRequest;
import com.agentsquad.squad.service.MessageAcceptanceService;
import com.agentsquad.squad.service.ConversationEventStreamerService;
import com.agentsquad.squad.service.RedisConversationNotificationsService;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不同应用实例的持久答案SSE交付
 * <p>
 * 同一JVM中的两个独立Spring容器共享隔离MySQL和Redis，执行端无页面连接，订阅端不启动模型。
 * 通过真实HTTP验证跨实例通知与通知丢失后的持久补读，不能用进程内事件代替跨实例通信。
 */
class CrossInstanceSseIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(45)
    void anotherInstanceReceivesFinalAnswerOrReconcilesMissedNotice(boolean missNotice, @TempDir Path directory) throws Exception {
        String user = "00000000-0000-0000-0000-000000000001";
        String database = MySqlTestDatabase.url(directory);
        String namespace = "squad:notify-test:" + UUID.randomUUID();
        try (var model = new NaturalReplyModelStub();
             var execution = start(database, namespace, model.baseUrl(), false);
             var page = start(database, namespace, model.baseUrl(), true);
             var client = HttpClient.newHttpClient()) {
            var accepted = execution.getBean(MessageAcceptanceService.class).accept(user, null, "cross-instance",
                    new MessageCreateRequest("验证其他实例收到最终答案", List.of()));
            assertThat(model.entered.await(10, TimeUnit.SECONDS)).isTrue();
            var jdbc = execution.getBean(JdbcTemplate.class);
            long sequence = jdbc.queryForObject("SELECT MAX(event_seq) FROM sq_conversation_event WHERE conversation_id=?",
                    Long.class, accepted.conversationId());
            int port = ((ServletWebServerApplicationContext) page).getWebServer().getPort();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                            + "/api/v1/conversations/" + accepted.conversationId() + "/events"))
                    .timeout(Duration.ofSeconds(15)).header("X-User-Id", user).header("X-User-Role", "USER")
                    .header("Last-Event-ID", accepted.conversationId() + ":0:" + sequence).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);
            try (var body = response.body()) {
                var previewSeen = new java.util.concurrent.CountDownLatch(1);
                var received = CompletableFuture.supplyAsync(() -> {
                    try {
                        var reader = new BufferedReader(new InputStreamReader(body));
                        var json = new ObjectMapper();
                        String line;
                        String eventName = "";
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) {
                                eventName = "";
                                continue;
                            }
                            if (line.startsWith("event:")) {
                                eventName = line.substring(6).trim();
                            }
                            if (line.startsWith("data:")) {
                                var event = json.readTree(line.substring(5).trim());
                                if ("output.preview".equals(eventName)) {
                                    assertThat(event.path("text").asText()).doesNotContain("TEST_PRIVATE_REASONING");
                                    assertThat(event.path("preview").asBoolean()).isTrue();
                                    if ("THINKING".equals(event.path("phase").asText())) {
                                        continue;
                                    }
                                    assertThat(event.path("phase").asText()).isEqualTo("ANSWERING");
                                    if (previewSeen.getCount() > 0) {
                                        assertThat(event.path("text").asText()).isEqualTo("页面断开后，");
                                    }
                                    previewSeen.countDown();
                                }
                                if ("message.completed".equals(event.path("type").asText())) {
                                    return event.path("payload").path("message").path("text").asText();
                                }
                            }
                        }
                        throw new IllegalStateException("SSE未收到终稿就关闭");
                    } catch (Exception failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                    }
                });
                if (missNotice) {
                    var notifications = page.getBean(RedisConversationNotificationsService.class);
                    var listener = (RedisMessageListenerContainer) ReflectionTestUtils.getField(notifications, "listener");
                    listener.stop();
                }
                model.firstChunk.countDown();
                if (!missNotice) {
                    boolean arrived = previewSeen.await(5, TimeUnit.SECONDS);
                    if (!arrived && received.isCompletedExceptionally()) {
                        received.get();
                    }
                    assertThat(arrived).as("模型结束前收到首段公开正文").isTrue();
                    assertThat(model.release.getCount()).isEqualTo(1);
                }
                model.release.countDown();
                ResponseNativeExecutionIntegrationTest.await(execution, accepted, "SUCCEEDED");
                if (missNotice) {
                    assertThat(received.isDone()).isFalse();
                    page.getBean(ConversationEventStreamerService.class).reconcile();
                }
                assertThat(received.get(10, TimeUnit.SECONDS)).isEqualTo("页面断开后，回答仍然完成。");
                assertThat(model.calls.get()).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE conversation_id=? AND role='ASSISTANT'",
                        Integer.class, accepted.conversationId())).isEqualTo(1);
            }
        }
    }

    private ConfigurableApplicationContext start(String database, String namespace, String model, boolean page) {
        var properties = new ArrayList<>(List.of(
                "--spring.datasource.url=" + database, "--spring.datasource.username=" + MySqlTestDatabase.username(),
                "--spring.datasource.password=" + MySqlTestDatabase.password(), "--server.shutdown=immediate",
                "--agentsquad.identity.allow-unsigned-local=true", "--agentsquad.delivery.enabled=" + !page,
                "--agentsquad.events.reconcile-delay-ms=3600000", "--agentsquad.events.redis-reconnect-delay-ms=3600000",
                "--agentsquad.redis-wakeup.enabled=true", "--agentsquad.redis-wakeup.namespace=" + namespace,
                "--spring.data.redis.host=127.0.0.1", "--spring.data.redis.port=" + System.getenv("SQUAD_TEST_REDIS_PORT"),
                "--spring.data.redis.password=" + System.getenv("SQUAD_TEST_REDIS_PASSWORD"),
                "--spring.data.redis.timeout=300ms", "--spring.data.redis.connect-timeout=300ms",
                "--agentsquad.model.api-key=test-only", "--agentsquad.model.name=test-model", "--agentsquad.model.base-url=" + model,
                "--agentsquad.worker.model-profiles.default.api-key=test-only",
                "--agentsquad.worker.model-profiles.default.model-name=test-model",
                "--agentsquad.worker.model-profiles.default.base-url=" + model));
        properties.add(page ? "--server.port=0" : "--spring.main.web-application-type=none");
        return SpringApplication.run(AgentSquadApplication.class, properties.toArray(String[]::new));
    }
}
