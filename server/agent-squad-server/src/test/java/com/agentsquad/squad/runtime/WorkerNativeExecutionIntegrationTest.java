package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.vo.ConversationVO;
import com.agentsquad.squad.vo.TaskReceiptVO;
import com.agentsquad.squad.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.bo.PlanNodeBO;
import com.agentsquad.squad.bo.PlanPublishBO;
import com.agentsquad.squad.bo.PlanReceiptBO;
import com.agentsquad.squad.service.ContextSnapshotService;
import com.agentsquad.squad.service.PlanService;
import com.agentsquad.squad.service.WorkerDefinitionService;
import com.agentsquad.squad.dto.WorkerDefinitionCreateDTO;
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
import com.agentsquad.squad.enums.AccountRole;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 新 Worker Definition 从计划根节点到原生回合及 Job 终态的正向实验。
 *
 * 模型端由本地 OpenAI 兼容桩提供，验证真实 SDK 会话和平台投递、投影；不代表外部模型可用性。
 */
class WorkerNativeExecutionIntegrationTest {

    @Test
    void importsLegacyJournalInboxBlobsAndExportCursorWithoutOverwritingDatabase(@TempDir Path directory) throws Exception {
        String user = "00000000-0000-0000-0000-000000000001";
        Path legacy = directory.resolve("legacy");
        var context = io.agentscope.core.agent.RuntimeContext.builder().userId(user).sessionId("legacy-session").build();
        var key = new io.agentscope.core.session.SessionKey(user, "legacy-agent", "legacy-session");
        var local = new io.agentscope.harness.agent.session.WorkspaceSessionLogStore(
                new io.agentscope.harness.agent.filesystem.local.LocalFilesystem(legacy.resolve(user)));
        var source = local.open(key, context);
        var event = new io.agentscope.core.session.SessionEvent(1, "legacy-event", 1, 1000,
                "state/checkpoint", "run", "turn", true, "{\"data\":\"" + "x".repeat(70000) + "\"}");
        var writer = source.acquire("old", java.time.Duration.ofSeconds(30));
        source.commit(writer, "legacy-batch", 0, List.of(event));
        source.release(writer);
        source.advanceExportCursor("test-export", 0, 1);
        var inbox = source.inbox();
        var inboxWriter = inbox.acquire("input", java.time.Duration.ofSeconds(30));
        var input = new io.agentscope.core.session.SessionEvent(1, "input-event", 1, 1001,
                "inbox/submitted", null, "turn", true, "{\"commandId\":\"command\"}");
        inbox.commit(inboxWriter, "input-batch", 0, List.of(input));
        inbox.release(inboxWriter);
        try (var spring = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("database")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(),
                "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--agentsquad.delivery.enabled=false")) {
            var importer = spring.getBean(com.agentsquad.squad.service.RuntimeJournalImportService.class);
            var imported = importer.importWorkspace(legacy);
            assertThat(imported.sessions()).isEqualTo(1);
            assertThat(imported.bytes()).isGreaterThan(70000);
            var store = spring.getBean(com.agentsquad.squad.agent.session.RuntimeSessionProvider.class).logStore();
            var target = store.open(key, context);
            assertThat(target.readAfter(0, 10)).containsExactly(event);
            assertThat(target.inbox().readAfter(0, 10)).containsExactly(input);
            assertThat(target.exportCursor("test-export")).isEqualTo(1);
            assertThat(importer.importWorkspace(legacy)).isEqualTo(imported);
            var nextWriter = target.acquire("new", java.time.Duration.ofSeconds(30));
            target.commit(nextWriter, "new-batch", 1, List.of(new io.agentscope.core.session.SessionEvent(
                    1, "new-event", 2, 1002, "run/end", "new-run", "turn", true, "{\"status\":\"completed\"}")));
            target.release(nextWriter);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> importer.importWorkspace(legacy))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("拒绝覆盖");
            assertThat(target.head().seq()).isEqualTo(2);
        }
    }

    @Test
    void newlyRegisteredWorkerRunsAndFinalizes(@TempDir Path directory) throws Exception {
        AtomicInteger modelCalls = new AtomicInteger();
        var activeDatabase = new java.util.concurrent.atomic.AtomicReference<JdbcTemplate>();
        HttpServer model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        model.createContext("/v1/chat/completions", exchange -> {
            int sequence = modelCalls.incrementAndGet();
            String answer = switch (sequence) {
                case 1 -> toolCallResponse();
                case 2 -> completionToolCallResponse(activeDatabase.get());
                default -> completionFinalResponse();
            };
            byte[] response = answer
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        model.start();
        try {
            String database = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("squad"));
            String baseUrl = "http://127.0.0.1:" + model.getAddress().getPort() + "/v1";
            String[] base = {
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
                    "--agentsquad.worker.model-profiles.review.provider=OPENAI_COMPATIBLE",
                    "--agentsquad.worker.model-profiles.review.api-key=test-only",
                    "--agentsquad.worker.model-profiles.review.base-url=" + baseUrl,
                    "--agentsquad.worker.model-profiles.review.model-name=test-model",
                    "--agentsquad.worker.model-profiles.review.pricing-version=review-v1",
                    "--agentsquad.worker.model-profiles.review.input-microunits-per-million-tokens=2000000",
                    "--agentsquad.worker.model-profiles.review.output-microunits-per-million-tokens=3000000",
                    "--agentsquad.worker.model-profiles.review.max-input-tokens=128",
                    "--agentsquad.worker.model-profiles.review.max-output-tokens=64",
                    "--agentsquad.worker.runtime-profiles.review.max-model-calls=4",
                    "--agentsquad.worker.runtime-profiles.review.max-tool-calls=2",
                    "--agentsquad.worker.runtime-profiles.review.max-recovery-attempts=1",
                    "--agentsquad.worker.runtime-profiles.review.model-request-timeout-seconds=30",
                    "--agentsquad.worker.runtime-profiles.review.tool-timeout-seconds=10",
                    "--agentsquad.delivery.poll-delay-ms=100",
                    "--agentsquad.projection.poll-delay-ms=100"
            };
            String userId = "00000000-0000-0000-0000-000000000001";
            String jobId;
            String taskId;
            try (ConfigurableApplicationContext spring = SpringApplication.run(
                    AgentSquadApplication.class, base)) {
                GoalTaskService tasks = spring.getBean(GoalTaskService.class);
                ContextSnapshotService snapshots = spring.getBean(ContextSnapshotService.class);
                PlanService plans = spring.getBean(PlanService.class);
                WorkerDefinitionService definitions = spring.getBean(WorkerDefinitionService.class);
                ObjectMapper json = spring.getBean(ObjectMapper.class);
                JdbcTemplate jdbc = spring.getBean(JdbcTemplate.class);
                ConversationVO conversation = tasks.createConversation(userId, "写作任务");
                TaskReceiptVO task = tasks.submit(userId, conversation.conversationId(),
                        "native-worker", "写一段摘要");
                taskId = task.taskId();
                definitions.publish(new AuthView(userId, "admin", List.of(AccountRole.ADMIN), "csrf"),
                        "new-writer", new WorkerDefinitionCreateDTO(
                                "new-writer", "1.0.0", "写简短摘要",
                                json.readTree("{}"),
                                json.readTree("{\"type\":\"object\",\"required\":[\"text\"],"
                                        + "\"properties\":{\"text\":{\"type\":\"string\"}}}"),
                                List.of(), "review", "review"));
                String masterSession = jdbc.queryForObject("""
                        SELECT framework_session_id FROM sq_runtime_binding
                        WHERE task_id = ? AND role = 'MASTER'
                        """, String.class, task.taskId());
                String snapshot = snapshots.prepare(userId, masterSession,
                        "write", "native-snapshot").snapshotId();
                PlanReceiptBO plan = plans.publish(userId, masterSession,
                        new PlanPublishBO("native-plan", 0, List.of(
                                new PlanNodeBO("write", "new-writer", "1.0.0", "写一段摘要",
                                        snapshot, List.of(), true, 1)),
                                "ALL_REQUIRED", List.of("摘要完成")));
                jobId = plan.jobIds().getFirst();
                jdbc.update("UPDATE sq_command SET status = 'CANCELLED' WHERE command_id = ?",
                        task.commandId());
            }

            String[] running = java.util.Arrays.copyOf(base, base.length + 1);
            running[base.length] = "--agentsquad.delivery.enabled=true";
            try (ConfigurableApplicationContext spring = SpringApplication.run(
                    AgentSquadApplication.class, running)) {
                JdbcTemplate jdbc = spring.getBean(JdbcTemplate.class);
                activeDatabase.set(jdbc);
                String status = null;
                for (int attempt = 0; attempt < 100; attempt++) {
                    status = jdbc.queryForObject(
                            "SELECT status FROM sq_worker_job WHERE job_id = ?", String.class, jobId);
                    if ("SUCCEEDED".equals(status) || "RECOVERY_BLOCKED".equals(status)) {
                        break;
                    }
                    Thread.sleep(100);
                }
                assertThat(status).isEqualTo("SUCCEEDED");
                String taskStatus = null;
                for (int attempt = 0; attempt < 100; attempt++) {
                    taskStatus = jdbc.queryForObject(
                            "SELECT status FROM sq_goal_task WHERE task_id = ?", String.class, taskId);
                    if ("SUCCEEDED".equals(taskStatus) || "RECOVERY_BLOCKED".equals(taskStatus)) {
                        break;
                    }
                    Thread.sleep(100);
                }
                assertThat(taskStatus).isEqualTo("SUCCEEDED");
                assertThat(modelCalls.get()).isEqualTo(3);
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM sq_budget_reservation
                        WHERE task_id = (SELECT task_id FROM sq_worker_job WHERE job_id = ?)
                          AND kind = 'MODEL' AND model_profile_key = 'review'
                          AND pricing_version = 'review-v1' AND status = 'SETTLED'
                        """, Integer.class, jobId)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT result_json FROM sq_worker_job WHERE job_id = ?",
                        String.class, jobId)).contains("hello");
                assertThat(jdbc.queryForObject("""
                        SELECT payload_json FROM sq_task_event
                        WHERE task_id = ? AND event_type = 'answer.completed'
                        """, String.class, taskId)).contains("摘要已完成：hello");
            }
        } finally {
            model.stop(0);
        }
    }

    private String toolCallResponse() {
        String arguments = new ObjectMapper().valueToTree(
                "{\"result\":{\"text\":\"hello\"}}").toString();
        return """
                {"id":"chatcmpl-worker-1","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"call-result-1","type":"function",
                     "function":{"name":"report_job_result","arguments":%s}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """.formatted(arguments);
    }

    private String finalResponse() {
        return """
                {"id":"chatcmpl-worker-2","object":"chat.completion","created":2,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"已提交结果。"},
                   "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """;
    }

    private String completionToolCallResponse(JdbcTemplate jdbc) {
        var json = new ObjectMapper();
        String arguments = json.valueToTree(json.valueToTree(
                com.agentsquad.squad.testsupport.ModelAcceptanceFixture.completion(json, jdbc,
                        "SUCCEEDED", "摘要已完成：hello")).toString()).toString();
        return """
                {"id":"chatcmpl-master-1","object":"chat.completion","created":3,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"call-completion-1","type":"function",
                     "function":{"name":"propose_completion","arguments":%s}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """.formatted(arguments);
    }

    private String completionFinalResponse() {
        return """
                {"id":"chatcmpl-master-2","object":"chat.completion","created":4,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"等待平台发布最终回答。"},
                   "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """;
    }
}
