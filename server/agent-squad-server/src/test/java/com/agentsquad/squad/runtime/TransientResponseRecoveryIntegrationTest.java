package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.dto.ActivityHistoryDTO;
import com.agentsquad.squad.dto.MessageCreateDTO;
import com.agentsquad.squad.service.MessageAcceptanceService;
import com.agentsquad.squad.service.ResponseRetryService;
import com.agentsquad.squad.service.ConversationQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实SDK的暂时服务失败与有限恢复实验。
 * <p>
 * 本地HTTP模型返回结构化503，验证两次上限、未知收费保留与成功后继；不推断真实供应商质量。
 */
@ExtendWith(OutputCaptureExtension.class)
class TransientResponseRecoveryIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"http", "http-exhaust", "eof", "eof-exhaust", "partial", "partial-exhaust"})
    void transientFailureRecoversOrStopsAtPersistentLimit(String scenario, @TempDir Path directory, CapturedOutput logs) throws Exception {
        boolean exhaust = scenario.endsWith("-exhaust");
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean unavailable = new AtomicBoolean(exhaust);
        ObjectMapper json = new ObjectMapper();
        HttpServer model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        model.createContext("/v1/chat/completions", exchange -> {
            var request = json.readTree(exchange.getRequestBody().readAllBytes());
            int call = calls.incrementAndGet();
            String body;
            int status;
            if (call == 1 || unavailable.get()) {
                if (!scenario.startsWith("http")) {
                    byte[] fragment = (scenario.startsWith("partial") ? "data: {\"id\":\"broken\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"未完成的旧预览\"},\"finish_reason\":null}]}\n\n" : "")
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.getResponseHeaders().set("Connection", "close");
                    exchange.sendResponseHeaders(200, fragment.length + 100);
                    try (var output = exchange.getResponseBody()) {
                        output.write(fragment);
                        output.flush();
                    } catch (java.io.IOException expectedTruncation) {
                        // 夹具故意少写声明长度，真实HTTP客户端必须识别传输中断。
                    } finally {
                        exchange.close();
                    }
                    return;
                }
                status = 503;
                body = "{\"error\":{\"message\":\"private-test-provider-detail\",\"type\":\"server_error\"}}";
            } else {
                status = 200;
                if (request.path("stream").asBoolean()) {
                    body = """
                            data: {"id":"completion-%d","choices":[{"index":0,"delta":{"content":"恢复后的回答"},"finish_reason":null}]}

                            data: {"id":"completion-%d","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                            data: {"id":"completion-%d","choices":[],"usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}

                            data: [DONE]

                            """.formatted(call, call, call);
                } else {
                    body = """
                            {"id":"completion-%d","object":"chat.completion","created":1,"model":"test-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"恢复后的回答"},"finish_reason":"stop"}],
                             "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                            """.formatted(call);
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", status == 200 && request.path("stream").asBoolean() ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        model.start();
        String base = "http://127.0.0.1:" + model.getAddress().getPort() + "/v1";
        try (var context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none", "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("recovery")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=100", "--agentsquad.projection.poll-delay-ms=100",
                "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=" + base, "--agentsquad.model.name=test-model",
                "--agentsquad.worker.model-profiles.default.api-key=test-only", "--agentsquad.worker.model-profiles.default.base-url=" + base, "--agentsquad.worker.model-profiles.default.model-name=test-model")) {
            String user = "00000000-0000-0000-0000-000000000001";
            var message = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateDTO("请给出回答", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String status = awaitStatus(jdbc, message.executionId());
            assertThat(status).isEqualTo(exhaust ? "FAILED" : "SUCCEEDED");
            assertThat(calls.get()).isEqualTo(exhaust ? 3 : 2);
            String scope = jdbc.queryForObject("SELECT scope_id FROM sq_response_execution WHERE execution_id=?", String.class, message.executionId());
            assertThat(jdbc.queryForObject("SELECT service_retry_attempts FROM sq_execution_scope WHERE scope_id=?", Integer.class, scope)).isEqualTo(exhaust ? 2 : 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE status='UNKNOWN'", Integer.class)).isEqualTo(exhaust ? 3 : 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Integer.class)).isZero();
            var reads = context.getBean(ConversationQueryService.class);
            assertThat(json.writeValueAsString(reads.snapshot(user, message.conversationId(), null, 40))).doesNotContain("private-test-provider-detail");
            if (exhaust) {
                var failure = reads.snapshot(user, message.conversationId(), null, 40).failures().getFirst();
                assertThat(failure.allowedActions()).containsExactly("RETRY");
                unavailable.set(false);
                var successor = context.getBean(ResponseRetryService.class).retry(user, message.executionId(), "manual-retry", failure.expectedVersion());
                assertThat(awaitStatus(jdbc, successor.executionId())).isEqualTo("SUCCEEDED");
                assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, message.executionId())).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account", Integer.class)).isEqualTo(1);
                assertThat(calls.get()).isEqualTo(4);
            }
            assertThat(reads.snapshot(user, message.conversationId(), null, 40).messages()).anyMatch(item -> "恢复后的回答".equals(item.text()));
            assertThat(reads.snapshot(user, message.conversationId(), null, 40).failures()).isEmpty();
            var history = reads.activityHistory(user, message.conversationId(),
                    new ActivityHistoryDTO(message.messageId(), null));
            assertThat(history.items().stream().filter(item -> item.type().name().equals("AUTO_RETRY_SCHEDULED")))
                    .hasSize(exhaust ? 2 : 1);
            assertThat(history.items().stream().filter(item -> item.type().name().equals("USER_RETRY_SCHEDULED")))
                    .hasSize(exhaust ? 1 : 0);
            assertThat(reads.snapshot(user, message.conversationId(), null, 40).messages())
                    .noneMatch(item -> item.text().contains("未完成的旧预览"));
        } finally {
            model.stop(0);
        }
        assertThat(logs.getAll()).contains("模型调用失败").doesNotContain("private-test-provider-detail");
    }

    private static String awaitStatus(JdbcTemplate jdbc, String execution) throws InterruptedException {
        String status = null;
        for (int attempt = 0; attempt < 500; attempt++) {
            status = jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, execution);
            if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(status)) {
                return status;
            }
            Thread.sleep(100);
        }
        return status;
    }
}
