package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 从普通消息到独立目标、两个依赖Worker及最终结果的正式SDK链路实验。
 * 模型由本地协议桩驱动，验证调度与持久语义，不推断真实模型的规划质量。
 */
class GoalTaskNativeCreationIntegrationTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replyCreatesIndependentGoalAndRunsDependentWorkers(boolean queryDuringWork, @TempDir Path directory) throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtomicInteger masterCalls = new AtomicInteger();
        AtomicInteger replyCalls = new AtomicInteger();
        AtomicInteger workerCalls = new AtomicInteger();
        AtomicInteger progressCalls = new AtomicInteger();
        AtomicReference<String> progressRequest = new AtomicReference<>();
        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        var providerThreads = Executors.newFixedThreadPool(4);
        AtomicReference<JdbcTemplate> jdbcRef = new AtomicReference<>();
        AtomicReference<String> taskIdRef = new AtomicReference<>();
        HttpServer model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        model.setExecutor(providerThreads);
        model.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
            boolean worker = false;
            for (JsonNode tool : request.path("tools")) {
                if ("report_job_result".equals(tool.path("function").path("name").asText())) {
                    worker = true;
                }
            }
            String response;
            if (worker) {
                int workerCall = workerCalls.incrementAndGet();
                if (queryDuringWork && workerCall == 1) {
                    workerBlocked.countDown();
                    try {
                        releaseWorker.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                response = workerCall % 2 == 1
                        ? toolResponse(json, "report_job_result", Map.of("result", Map.of("text", "成稿")), 5)
                        : textResponse("Worker 已提交结果", 6);
            } else if (request.path("messages").toString().contains("目前做到哪一步了")) {
                progressRequest.set(request.path("messages").toString());
                response = progressCalls.incrementAndGet() == 1
                        ? toolResponse(json, "propose_completion", Map.of("outcome", "SUCCEEDED",
                                "finalAnswer", "正在拟提纲，成稿尚未开始。"), 201)
                        : textResponse("等待平台发布进度。", 202);
            } else if (request.path("messages").toString().contains("请回复来源用户消息")) {
                int call = replyCalls.incrementAndGet();
                String source = jdbcRef.get().queryForObject("SELECT source_message_id FROM sq_response_execution", String.class);
                response = switch (call) {
                    case 1 ->
                            toolResponse(json, "create_goal_task", Map.of("logicalKey", "writing-goal", "title", "短文写作",
                                    "objective", "先拟提纲，再写短文", "sourceMessageId", source), 101);
                    case 2 ->
                            toolResponse(json, "propose_completion", Map.of("outcome", "SUCCEEDED", "finalAnswer", "已开始处理，完成后给你结果。"), 102);
                    default -> textResponse("等待平台发布受理说明。", 103);
                };
            } else {
                int call = masterCalls.incrementAndGet();
                response = switch (call) {
                    case 1 -> toolResponse(json, "list_worker_capabilities", Map.of(), call);
                    case 2 -> toolResponse(json, "prepare_context",
                            Map.of("targetNodeKey", "outline", "logicalKey", "outline-context"), call);
                    case 3 -> toolResponse(json, "prepare_context",
                            Map.of("targetNodeKey", "draft", "logicalKey", "draft-context"), call);
                    case 4 -> toolResponse(json, "publish_plan",
                            Map.of("logicalKey", "writing-plan", "expectedPlanVersion", 0,
                                    "nodes", List.of(Map.of(
                                            "nodeKey", "outline", "workerKey", "draft-writer",
                                            "workerVersion", "1.0.0", "goal", "先拟提纲",
                                            "contextSnapshotId", jdbcRef.get().queryForObject(
                                                    "SELECT snapshot_id FROM sq_context_snapshot WHERE target_node_key='outline'", String.class),
                                            "dependsOn", List.of(), "required", true, "maxRecoveryAttempts", 1), Map.of(
                                            "nodeKey", "draft", "workerKey", "draft-writer",
                                            "workerVersion", "1.0.0", "goal", "写一段短文",
                                            "contextSnapshotId", jdbcRef.get().queryForObject(
                                                    "SELECT snapshot_id FROM sq_context_snapshot WHERE target_node_key='draft'",
                                                    String.class),
                                            "dependsOn", List.of("outline"), "required", true,
                                            "maxRecoveryAttempts", 1)),
                                    "joinPolicy", "ALL_REQUIRED", "acceptanceCriteria", List.of("短文完成")), call);
                    case 5 -> textResponse("计划已发布，等待 Worker。", call);
                    case 6 -> toolResponse(json, "propose_completion",
                            Map.of("outcome", "SUCCEEDED", "finalAnswer", "短文已完成：成稿"), call);
                    default -> textResponse("等待平台发布答案。", call);
                };
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        model.start();
        try {
            String database = "jdbc:h2:file:" + directory.resolve("squad")
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
            String baseUrl = "http://127.0.0.1:" + model.getAddress().getPort() + "/v1";
            String[] properties = {
                    "--spring.main.web-application-type=none",
                    "--spring.datasource.url=" + database,
                    "--spring.datasource.username=sa",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--agentsquad.model.api-key=test-only",
                    "--agentsquad.model.base-url=" + baseUrl,
                    "--agentsquad.model.name=test-model",
                    "--agentsquad.worker.model-profiles.default.api-key=test-only",
                    "--agentsquad.worker.model-profiles.default.base-url=" + baseUrl,
                    "--agentsquad.worker.model-profiles.default.model-name=test-model",
                    "--agentsquad.delivery.enabled=true",
                    "--agentsquad.delivery.poll-delay-ms=100",
                    "--agentsquad.projection.poll-delay-ms=100"
            };
            String userId = "00000000-0000-0000-0000-000000000001";
            try (ConfigurableApplicationContext spring = SpringApplication.run(
                    AgentSquadApplication.class, properties)) {
                JdbcTemplate jdbc = spring.getBean(JdbcTemplate.class);
                jdbcRef.set(jdbc);
                WorkerDefinitionService definitions = spring.getBean(WorkerDefinitionService.class);
                definitions.publish(new AuthView(userId, "admin", List.of("ADMIN"), "csrf"),
                        "draft-writer", new WorkerDefinitionCreateRequest(
                                "draft-writer", "1.0.0", "写作短文",
                                json.readTree("{}"), json.readTree("""
                                {"type":"object","required":["text"],
                                 "properties":{"text":{"type":"string"}}}
                                """), List.of(), "default", "default"));
                var message = spring.getBean(MessageAcceptanceService.class).accept(userId, null,
                        "autonomous-writing", new MessageCreateRequest("请先拟提纲，再写一段短文", List.of()));
                for (int attempt = 0; attempt < 100; attempt++) {
                    List<String> tasks = jdbc.queryForList("SELECT task_id FROM sq_goal_task WHERE origin_execution_id=?", String.class, message.executionId());
                    if (!tasks.isEmpty()) {
                        taskIdRef.set(tasks.getFirst());
                        break;
                    }
                    Thread.sleep(100);
                }
                assertThat(taskIdRef.get()).isNotBlank();
                if (queryDuringWork) {
                    assertThat(workerBlocked.await(10, TimeUnit.SECONDS)).isTrue();
                    ResponseNativeExecutionIntegrationTest.await(spring, message, "SUCCEEDED");
                    var progress = spring.getBean(MessageAcceptanceService.class).accept(userId, message.conversationId(),
                            "progress-query", new MessageCreateRequest("目前做到哪一步了？", List.of()));
                    ResponseNativeExecutionIntegrationTest.await(spring, progress, "SUCCEEDED");
                    assertThat(workerCalls.get()).isEqualTo(1);
                    assertThat(progressRequest.get()).contains("activeTask", "outline", "draft", "RUNNING", "BLOCKED");
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isEqualTo(1);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_plan", Long.class)).isEqualTo(1);
                    releaseWorker.countDown();
                }
                String status = null;
                for (int attempt = 0; attempt < 300; attempt++) {
                    status = jdbc.queryForObject("SELECT status FROM sq_goal_task WHERE task_id = ?",
                            String.class, taskIdRef.get());
                    if ("SUCCEEDED".equals(status) || "RECOVERY_BLOCKED".equals(status)
                            || "FAILED".equals(status)) {
                        break;
                    }
                    Thread.sleep(100);
                }
                assertThat(status).isEqualTo("SUCCEEDED");
                assertThat(masterCalls.get()).isEqualTo(7);
                assertThat(replyCalls.get()).isEqualTo(3);
                assertThat(workerCalls.get()).isEqualTo(4);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account", Long.class)).isEqualTo(queryDuringWork ? 2 : 1);
                assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT account_id) FROM sq_budget_reservation WHERE task_id IS NOT NULL", Long.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_worker_job WHERE status='SUCCEEDED'", Long.class)).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_runtime_binding WHERE task_id IS NULL", Long.class)).isEqualTo(queryDuringWork ? 2 : 1);
                assertThat(jdbc.queryForList("SELECT DISTINCT status FROM sq_response_execution", String.class)).containsExactly("SUCCEEDED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE status='SETTLED'", Long.class)).isEqualTo(queryDuringWork ? 16 : 14);
                assertThat(progressCalls.get()).isEqualTo(queryDuringWork ? 2 : 0);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event WHERE event_type='task.created'", Long.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sq_goal_task WHERE conversation_id = ?",
                        Integer.class, message.conversationId())).isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                        SELECT payload_json FROM sq_task_event
                        WHERE task_id = ? AND event_type = 'answer.completed'
                        """, String.class, taskIdRef.get())).contains("短文已完成：成稿");
            }
        } finally {
            releaseWorker.countDown();
            model.stop(0);
            providerThreads.shutdownNow();
        }
    }

    private String toolResponse(ObjectMapper json, String toolName,
                                Map<String, Object> arguments, int sequence) throws java.io.IOException {
        String encoded = json.writeValueAsString(json.writeValueAsString(arguments));
        return """
                {"id":"chatcmpl-%d","object":"chat.completion","created":%d,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"call-%d","type":"function",
                     "function":{"name":"%s","arguments":%s}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """.formatted(sequence, sequence, sequence, toolName, encoded);
    }

    private String textResponse(String content, int sequence) {
        return """
                {"id":"chatcmpl-%d","object":"chat.completion","created":%d,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},
                   "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """.formatted(sequence, sequence, content);
    }
}
