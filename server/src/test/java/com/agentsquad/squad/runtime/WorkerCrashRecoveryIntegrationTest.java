package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.integration.agentscope.CommandDeliveryService;
import com.agentsquad.squad.integration.agentscope.NativeProjectionPoller;
import com.agentsquad.squad.integration.agentscope.WorkerFactory;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.dto.PlanReceipt;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import com.agentsquad.squad.runtime.service.PlanService;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.harness.agent.session.AgentSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在候选结果已写入、原生回合尚未结束的窗口封存旧 writer，验证同一 Session/Turn 的恢复。
 * 第二个 Spring 实例接管固定 Binding；本地 HTTP 模型桩不会访问真实模型。
 */
class WorkerCrashRecoveryIntegrationTest {

    @Test
    void resumesOriginalWorkerTurnAfterWriterDisappears(@TempDir Path directory) throws Exception {
        AtomicInteger workerCalls = new AtomicInteger();
        AtomicInteger masterCalls = new AtomicInteger();
        ObjectMapper json = new ObjectMapper();
        CountDownLatch oldRequestBlocked = new CountDownLatch(1);
        CountDownLatch releaseOldRequest = new CountDownLatch(1);
        HttpServer model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService modelExecutor = Executors.newFixedThreadPool(4);
        model.setExecutor(modelExecutor);
        model.createContext("/v1/chat/completions", exchange -> {
            var request = json.readTree(exchange.getRequestBody().readAllBytes());
            boolean worker = false;
            for (var tool : request.path("tools")) {
                if ("report_job_result".equals(tool.path("function").path("name").asText())) {
                    worker = true;
                }
            }
            String response;
            if (worker) {
                int call = workerCalls.incrementAndGet();
                if (call == 1 || call == 3) {
                    response = reportResponse();
                } else if (call == 4) {
                    oldRequestBlocked.countDown();
                    try {
                        releaseOldRequest.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    response = workerFinalResponse();
                } else {
                    response = workerFinalResponse();
                }
            } else {
                response = masterCalls.incrementAndGet() == 1
                        ? completionResponse() : masterFinalResponse();
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
                    "--agentsquad.delivery.enabled=true"
            };
            String[] slow = java.util.Arrays.copyOf(base, base.length + 2);
            slow[base.length] = "--agentsquad.delivery.poll-delay-ms=60000";
            slow[base.length + 1] = "--agentsquad.projection.poll-delay-ms=60000";
            String userId = "00000000-0000-0000-0000-000000000001";
            try (ConfigurableApplicationContext first = SpringApplication.run(
                    AgentSquadApplication.class, slow)) {
                JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
                WorkerDefinitionService definitions = first.getBean(WorkerDefinitionService.class);
                definitions.publish(new AuthView(userId, "admin", List.of("ADMIN"), "csrf"),
                        "crash-writer", new WorkerDefinitionCreateRequest(
                                "crash-writer", "1.0.0", "写摘要",
                                json.readTree("{}"), json.readTree("{}"),
                                List.of(), "default", "default"));
                GoalTaskService tasks = first.getBean(GoalTaskService.class);
                ConversationResponse conversation = tasks.createConversation(userId, "崩溃恢复");
                TaskReceipt task = tasks.submit(userId, conversation.conversationId(),
                        "crash-goal", "写一段摘要");
                jdbc.update("UPDATE sq_command SET status = 'CANCELLED' WHERE command_id = ?",
                        task.commandId());
                String masterSession = jdbc.queryForObject("""
                        SELECT framework_session_id FROM sq_runtime_binding
                        WHERE task_id = ? AND role = 'MASTER'
                        """, String.class, task.taskId());
                String snapshot = first.getBean(ContextSnapshotService.class).prepare(
                        userId, masterSession, "draft", "draft-context").snapshotId();
                String otherSnapshot = first.getBean(ContextSnapshotService.class).prepare(
                        userId, masterSession, "outline", "outline-context").snapshotId();
                PlanReceipt plan = first.getBean(PlanService.class).publish(userId, masterSession,
                        new PlanPublish("crash-plan", 0, List.of(
                                new PlanNode("outline", "crash-writer", "1.0.0", "写提纲",
                                        otherSnapshot, List.of(), true, 1),
                                new PlanNode("draft", "crash-writer", "1.0.0", "写摘要",
                                        snapshot, List.of(), true, 1)),
                                "ALL_REQUIRED", List.of("摘要完成")));
                String successfulJobId = plan.jobIds().getFirst();
                String successfulBinding = jdbc.queryForObject(
                        "SELECT binding_id FROM sq_worker_job WHERE job_id = ?", String.class, successfulJobId);
                String successfulCommand = jdbc.queryForObject("""
                        SELECT command_id FROM sq_command WHERE binding_id = ? AND action = 'SUBMIT'
                        """, String.class, successfulBinding);
                first.getBean(CommandDeliveryService.class).deliver(successfulCommand);
                for (int attempt = 0; attempt < 100; attempt++) {
                    jdbc.update("UPDATE sq_runtime_binding SET next_projection_at = ? WHERE binding_id = ?",
                            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), successfulBinding);
                    first.getBean(NativeProjectionPoller.class).projectDue();
                    if ("SUCCEEDED".equals(jdbc.queryForObject(
                            "SELECT status FROM sq_worker_job WHERE job_id = ?", String.class, successfulJobId))) {
                        break;
                    }
                    Thread.sleep(50);
                }
                assertThat(jdbc.queryForObject("SELECT status FROM sq_worker_job WHERE job_id = ?",
                        String.class, successfulJobId)).isEqualTo("SUCCEEDED");
                String jobId = plan.jobIds().get(1);
                String bindingId = jdbc.queryForObject(
                        "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                        String.class, jobId);
                String commandId = jdbc.queryForObject("""
                        SELECT command_id FROM sq_command
                        WHERE binding_id = ? AND action = 'SUBMIT'
                        """, String.class, bindingId);
                String workerSession = jdbc.queryForObject("""
                        SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id = ?
                        """, String.class, bindingId);
                String agentId = jdbc.queryForObject("""
                        SELECT agent_id FROM sq_runtime_binding WHERE binding_id = ?
                        """, String.class, bindingId);
                first.getBean(CommandDeliveryService.class).deliver(commandId);
                assertThat(oldRequestBlocked.await(10, TimeUnit.SECONDS)).isTrue();
                for (int attempt = 0; attempt < 100; attempt++) {
                    String candidate = jdbc.queryForObject(
                            "SELECT result_candidate_json FROM sq_worker_job WHERE job_id = ?",
                            String.class, jobId);
                    if (candidate != null) {
                        break;
                    }
                    Thread.sleep(50);
                }
                assertThat(jdbc.queryForObject(
                        "SELECT result_candidate_json FROM sq_worker_job WHERE job_id = ?",
                        String.class, jobId)).contains("候选摘要");
                assertThat(jdbc.queryForObject("SELECT status FROM sq_worker_job WHERE job_id = ?",
                        String.class, jobId)).isEqualTo("RUNNING");
                AgentSession oldSession = first.getBean(WorkerFactory.class).sessionFor(
                        userId, bindingId, workerSession, agentId);
                String oldWriter = oldSession.log().head().owner();
                assertThat(oldWriter).isNotBlank();
                oldSession.log().sealWriter(oldWriter);
                jdbc.update("UPDATE sq_worker_job SET guard_lease_until = ? WHERE job_id = ?",
                        LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), jobId);

                String[] fast = java.util.Arrays.copyOf(base, base.length + 2);
                fast[base.length] = "--agentsquad.delivery.poll-delay-ms=100";
                fast[base.length + 1] = "--agentsquad.projection.poll-delay-ms=100";
                try (ConfigurableApplicationContext second = SpringApplication.run(
                        AgentSquadApplication.class, fast)) {
                    JdbcTemplate peer = second.getBean(JdbcTemplate.class);
                    String status = null;
                    for (int attempt = 0; attempt < 250; attempt++) {
                        status = peer.queryForObject(
                                "SELECT status FROM sq_worker_job WHERE job_id = ?",
                                String.class, jobId);
                        if ("SUCCEEDED".equals(status) || "RECOVERY_BLOCKED".equals(status)) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                    assertThat(status).isEqualTo("SUCCEEDED");
                    assertThat(peer.queryForObject("""
                            SELECT COUNT(*) FROM sq_command
                            WHERE binding_id = ? AND action = 'SUBMIT'
                            """, Integer.class, bindingId)).isEqualTo(1);
                    assertThat(peer.queryForObject("""
                            SELECT COUNT(*) FROM sq_command
                            WHERE binding_id = ? AND action = 'RESUME'
                            """, Integer.class, bindingId)).isEqualTo(1);
                    assertThat(peer.queryForObject("SELECT status FROM sq_worker_job WHERE job_id = ?",
                            String.class, successfulJobId)).isEqualTo("SUCCEEDED");
                    assertThat(peer.queryForObject("SELECT COUNT(*) FROM sq_command WHERE binding_id = ?",
                            Integer.class, successfulBinding)).isEqualTo(1);
                    assertThat(workerCalls.get()).isEqualTo(5);
                } finally {
                    releaseOldRequest.countDown();
                }
            }
        } finally {
            releaseOldRequest.countDown();
            model.stop(0);
            modelExecutor.shutdownNow();
        }
    }

    private String reportResponse() {
        String arguments = new ObjectMapper().valueToTree(
                "{\"result\":{\"text\":\"候选摘要\"}}").toString();
        return """
                {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"call-result","type":"function",
                     "function":{"name":"report_job_result","arguments":%s}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """.formatted(arguments);
    }

    private String workerFinalResponse() {
        return """
                {"id":"chatcmpl-2","object":"chat.completion","created":2,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"已提交结果"},
                   "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """;
    }

    private String completionResponse() {
        String arguments = new ObjectMapper().valueToTree(
                "{\"outcome\":\"SUCCEEDED\",\"finalAnswer\":\"摘要：候选摘要\"}").toString();
        return """
                {"id":"chatcmpl-3","object":"chat.completion","created":3,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"call-completion","type":"function",
                     "function":{"name":"propose_completion","arguments":%s}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """.formatted(arguments);
    }

    private String masterFinalResponse() {
        return """
                {"id":"chatcmpl-4","object":"chat.completion","created":4,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"等待平台发布答案"},
                   "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """;
    }
}
