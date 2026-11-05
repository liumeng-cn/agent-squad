package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 普通聊天真实HTTP与SSE验证
 * <p>
 * 使用隔离MySQL、真实SDK及本地模型，断开推送连接后检查后台完成与持久事件续读，不以模拟Emitter代替HTTP证据。
 */
class ConversationSsePushIntegrationTest {
    private static final String USER = "00000000-0000-0000-0000-000000000001";
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disconnectedSubscriberDoesNotCancelReplyAndReconnectReplaysResult(boolean reconnectWhileRunning, @TempDir Path directory) throws Exception {
        try (var model = new NaturalReplyModelStub();
             var context = SpringApplication.run(AgentSquadApplication.class,
                     "--server.port=0", "--server.shutdown=immediate", "--agentsquad.identity.allow-unsigned-local=true",
                     "--spring.datasource.url=" + MySqlTestDatabase.url(directory),
                     "--spring.datasource.username=" + MySqlTestDatabase.username(),
                     "--spring.datasource.password=" + MySqlTestDatabase.password(),
                     "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=3600000",
                     "--agentsquad.projection.poll-delay-ms=3600000", "--agentsquad.events.reconcile-delay-ms=3600000",
                     "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=" + model.baseUrl(),
                     "--agentsquad.model.name=test-model",
                     "--agentsquad.worker.model-profiles.default.api-key=test-only",
                     "--agentsquad.worker.model-profiles.default.base-url=" + model.baseUrl(),
                     "--agentsquad.worker.model-profiles.default.model-name=test-model")) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            String base = "http://127.0.0.1:" + port + "/api/v1/conversations";
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(request(base + "/messages").header("Content-Type", "application/json")
                        .header("Idempotency-Key", "natural-http")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"验证独立执行\",\"artifactIds\":[]}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(202);
                JsonNode receipt = json.readTree(response.body()).path("data");
                String conversation = receipt.path("conversationId").asText();
                String execution = receipt.path("executionId").asText();
                assertThat(model.entered.await(10, TimeUnit.SECONDS)).isTrue();
                String cursor = conversation + ":0:0";
                var jdbc = context.getBean(JdbcTemplate.class);
                int generation = jdbc.queryForObject("SELECT generation FROM sq_conversation WHERE conversation_id=?", Integer.class, conversation);
                cursor = conversation + ":" + generation + ":0";
                var first = client.send(request(base + "/" + conversation + "/events?after=" + cursor).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                assertThat(first.statusCode()).isEqualTo(200);
                String lastId = null;
                try (var reader = new BufferedReader(new InputStreamReader(first.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("id:")) {
                            lastId = line.substring(3).trim();
                        }
                        if (line.startsWith("data:")) {
                            JsonNode event = json.readTree(line.substring(5).trim());
                            assertThat(event.path("payload").path("schemaVersion").asInt()).isEqualTo(2);
                            break;
                        }
                    }
                    model.firstChunk.countDown();
                    boolean preview = false;
                    String eventName = "";
                    boolean previewHasId = false;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            eventName = "";
                            previewHasId = false;
                        }
                        if (line.startsWith("event:")) eventName = line.substring(6).trim();
                        if (line.startsWith("id:")) {
                            lastId = line.substring(3).trim();
                            previewHasId = true;
                        }
                        if (line.startsWith("data:") && "output.preview".equals(eventName)) {
                            JsonNode payload = json.readTree(line.substring(5).trim());
                            assertThat(payload.path("text").asText()).isEqualTo("页面断开后，").doesNotContain("TEST_PRIVATE_REASONING");
                            assertThat(payload.path("preview").asBoolean()).isTrue();
                            assertThat(previewHasId).isFalse();
                            preview = true;
                            break;
                        }
                    }
                    assertThat(preview).isTrue();
                    assertThat(model.release.getCount()).isEqualTo(1);
                }
                assertThat(lastId).isNotBlank();
                // HTTP连接关闭后才让模型完成；订阅与执行必须没有取消依赖。
                HttpResponse<java.io.InputStream> replay = null;
                if (reconnectWhileRunning) {
                    replay = client.send(request(base + "/" + conversation + "/events")
                            .header("Last-Event-ID", lastId).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                    assertThat(replay.statusCode()).isEqualTo(200);
                }
                model.release.countDown();
                String status = null;
                for (int i = 0; i < 500; i++) {
                    status = jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, execution);
                    if ("SUCCEEDED".equals(status) || "FAILED".equals(status) || "PARTIAL".equals(status)) {
                        break;
                    }
                    Thread.sleep(50);
                }
                assertThat(status).isEqualTo("SUCCEEDED");
                if (replay == null) {
                    replay = client.send(request(base + "/" + conversation + "/events")
                            .header("Last-Event-ID", lastId).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                    assertThat(replay.statusCode()).isEqualTo(200);
                }
                boolean found = false;
                try (var reader = new BufferedReader(new InputStreamReader(replay.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        JsonNode event = json.readTree(line.substring(5).trim());
                        if ("message.completed".equals(event.path("type").asText())) {
                            assertThat(event.path("payload").path("message").path("text").asText())
                                    .isEqualTo("页面断开后，回答仍然完成。");
                            found = true;
                            break;
                        }
                    }
                }
                assertThat(found).isTrue();
                assertThat(model.calls.get()).isEqualTo(1);
                assertThat(model.requests.getFirst()).doesNotContain("\"name\":\"propose_completion\"");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE conversation_id=? AND role='ASSISTANT'",
                        Integer.class, conversation)).isEqualTo(1);
                var snapshot = client.send(request(base + "/" + conversation + "/snapshot").GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(snapshot.statusCode()).isEqualTo(200);
                assertThat(json.readTree(snapshot.body()).path("data").path("messages").size()).isEqualTo(2);
                assertThat(model.calls.get()).isEqualTo(1);
                // 隔离夹具模拟终态后的未收件尾部；只回退测试游标，原journal与已发布终稿保持不变。
                String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, execution);
                long committedCursor = jdbc.queryForObject("SELECT native_event_cursor FROM sq_runtime_binding WHERE binding_id=?", Long.class, binding);
                jdbc.update("DELETE FROM sq_runtime_event_inbox WHERE binding_id=? AND native_seq=?", binding, committedCursor);
                jdbc.update("UPDATE sq_runtime_binding SET native_event_cursor=?,next_projection_at='2000-01-01' WHERE binding_id=?", committedCursor - 1, binding);
                context.getBean(com.agentsquad.squad.integration.agentscope.RuntimeProjectionDispatcher.class).retryPending();
                long restored = 0;
                for (int i = 0; i < 100; i++) {
                    restored = jdbc.queryForObject("SELECT native_event_cursor FROM sq_runtime_binding WHERE binding_id=?", Long.class, binding);
                    if (restored == committedCursor) break;
                    Thread.sleep(50);
                }
                assertThat(restored).isEqualTo(committedCursor);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE conversation_id=? AND role='ASSISTANT'", Integer.class, conversation)).isEqualTo(1);
                assertThat(model.calls.get()).isEqualTo(1);
            }
        }
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("X-User-Id", USER).header("X-User-Role", "USER");
    }
}
