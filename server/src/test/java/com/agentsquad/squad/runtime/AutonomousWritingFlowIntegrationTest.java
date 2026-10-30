package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 从用户目标到 Master 自主规划、Worker 执行、内部汇合和最终答案的原生链路实验。
 * 模型由本地协议桩驱动，验证调度与持久语义，不推断真实模型的规划质量。
 */
class AutonomousWritingFlowIntegrationTest {

    @Test
    void masterPlansWorkerAndFinishesOneGoal(@TempDir Path directory) throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtomicInteger masterCalls = new AtomicInteger();
        AtomicInteger workerCalls = new AtomicInteger();
        AtomicReference<JdbcTemplate> jdbcRef = new AtomicReference<>();
        AtomicReference<String> taskIdRef = new AtomicReference<>();
        HttpServer model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
                response = workerCalls.incrementAndGet() == 1
                        ? toolResponse(json, "report_job_result", Map.of("result", Map.of("text", "成稿")), 5)
                        : textResponse("Worker 已提交结果", 6);
            } else {
                int call = masterCalls.incrementAndGet();
                response = switch (call) {
                    case 1 -> toolResponse(json, "list_worker_capabilities", Map.of(), call);
                    case 2 -> toolResponse(json, "prepare_context",
                            Map.of("targetNodeKey", "draft", "logicalKey", "draft-context"), call);
                    case 3 -> toolResponse(json, "publish_plan",
                            Map.of("logicalKey", "writing-plan", "expectedPlanVersion", 0,
                                    "nodes", List.of(Map.of(
                                            "nodeKey", "draft", "workerKey", "draft-writer",
                                            "workerVersion", "1.0.0", "goal", "写一段短文",
                                            "contextSnapshotId", jdbcRef.get().queryForObject(
                                                    "SELECT snapshot_id FROM sq_context_snapshot WHERE task_id = ?",
                                                    String.class, taskIdRef.get()),
                                            "dependsOn", List.of(), "required", true,
                                            "maxRecoveryAttempts", 1)),
                                    "joinPolicy", "ALL_REQUIRED", "acceptanceCriteria", List.of("短文完成")), call);
                    case 4 -> textResponse("计划已发布，等待 Worker。", call);
                    case 5 -> toolResponse(json, "propose_completion",
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
            String database = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("squad"));
            String baseUrl = "http://127.0.0.1:" + model.getAddress().getPort() + "/v1";
            String[] properties = {
                    "--spring.main.web-application-type=none",
                    "--spring.datasource.url=" + database,
                    "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                    "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
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
                GoalTaskService tasks = spring.getBean(GoalTaskService.class);
                ConversationResponse conversation = tasks.createConversation(userId, "写作目标");
                TaskReceipt task = tasks.submit(userId, conversation.conversationId(),
                        "autonomous-writing", "请写一段短文");
                taskIdRef.set(task.taskId());

                String status = null;
                for (int attempt = 0; attempt < 300; attempt++) {
                    status = jdbc.queryForObject("SELECT status FROM sq_goal_task WHERE task_id = ?",
                            String.class, task.taskId());
                    if ("SUCCEEDED".equals(status) || "RECOVERY_BLOCKED".equals(status)
                            || "FAILED".equals(status)) {
                        break;
                    }
                    Thread.sleep(100);
                }
                assertThat(status).isEqualTo("SUCCEEDED");
                assertThat(masterCalls.get()).isEqualTo(6);
                assertThat(workerCalls.get()).isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sq_goal_task WHERE conversation_id = ?",
                        Integer.class, conversation.conversationId())).isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                        SELECT payload_json FROM sq_task_event
                        WHERE task_id = ? AND event_type = 'answer.completed'
                        """, String.class, task.taskId())).contains("短文已完成：成稿");
            }
        } finally {
            model.stop(0);
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
