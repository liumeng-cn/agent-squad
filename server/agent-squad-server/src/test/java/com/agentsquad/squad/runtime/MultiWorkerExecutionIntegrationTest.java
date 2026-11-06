package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import com.agentsquad.squad.runtime.service.PlanService;
import com.agentsquad.squad.worker.service.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import com.agentsquad.squad.common.enums.identity.AccountRole;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 串行和真实并行的 Worker 原生执行验收。
 * <p>
 * 使用本地模型桩与隔离数据库，通过持久命令启动两个 Worker，再由 Master 汇合完成同一目标。
 */
class MultiWorkerExecutionIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void runsTwoNodesAndJoinsOneGoal(boolean parallel, @TempDir Path directory) throws Exception {
        runScenario(parallel, false, false, directory);
    }

    @Test
    void reportsPartialAfterUnrecoverableBranch(@TempDir Path directory) throws Exception {
        runScenario(false, true, false, directory);
    }

    @Test
    void stopsTwoActuallyRunningWorkersWithoutPublishingLateResults(@TempDir Path directory) throws Exception {
        runScenario(true, false, true, directory);
    }

    private void runScenario(boolean parallel, boolean failSecond, boolean cancel, Path directory) throws Exception {
        ObjectMapper json = new ObjectMapper();
        CountDownLatch firstCalls = new CountDownLatch(2);
        CountDownLatch cancelRelease = new CountDownLatch(1);
        AtomicBoolean overlapped = new AtomicBoolean(false);
        AtomicBoolean dependencyVisible = new AtomicBoolean(false);
        AtomicInteger calls = new AtomicInteger();
        var activeDatabase = new java.util.concurrent.atomic.AtomicReference<JdbcTemplate>();
        var executor = Executors.newFixedThreadPool(4);
        HttpServer model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        model.setExecutor(executor);
        model.createContext("/v1/chat/completions", exchange -> {
            var request = json.readTree(exchange.getRequestBody().readAllBytes());
            calls.incrementAndGet();
            boolean worker = request.path("tools").toString().contains("report_job_result");
            boolean hasToolResult = false;
            for (var message : request.path("messages")) {
                if ("tool".equals(message.path("role").asText())) {
                    hasToolResult = true;
                }
            }
            String response;
            if (hasToolResult && (worker || request.path("messages").toString().contains("\"name\":\"propose_completion\""))) {
                response = completion(json, null, null);
            } else if (worker) {
                if (parallel) {
                    firstCalls.countDown();
                    try {
                        if (firstCalls.await(5, TimeUnit.SECONDS)) {
                            overlapped.set(true);
                        }
                        if (cancel && !cancelRelease.await(15, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("取消验证未及时释放模型桩");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                } else if (request.path("messages").toString().contains("write-node-2")) {
                    dependencyVisible.set(request.path("messages").toString().contains("node-result"));
                }
                response = failSecond && request.path("messages").toString().contains("write-node-2")
                        ? completion(json, null, null)
                        : completion(json, "report_job_result", "{\"result\":{\"text\":\"node-result\"}}");
            } else if (failSecond && !hasToolResult) {
                String failedJob = activeDatabase.get().queryForObject("SELECT job_id FROM sq_worker_job WHERE status='RECOVERY_BLOCKED'", String.class);
                response = completion(json, "abandon_failed_job", json.writeValueAsString(java.util.Map.of("jobId", failedJob, "expectedPlanVersion", 1)));
            } else {
                response = completion(json, "propose_completion", json.writeValueAsString(
                        com.agentsquad.squad.testsupport.ModelAcceptanceFixture.completion(json, activeDatabase.get(),
                                failSecond ? "PARTIAL" : "SUCCEEDED", failSecond
                                        ? "草稿已完成；第二项未产生可用结果，需要补充处理" : "两个子任务已汇总")));
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        model.start();
        String url = "http://127.0.0.1:" + model.getAddress().getPort() + "/v1";
        String[] base = {"--spring.main.web-application-type=none",
                "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("squad")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=" + url,
                "--agentsquad.model.name=test-model", "--agentsquad.worker.model-profiles.default.api-key=test-only",
                "--agentsquad.worker.model-profiles.default.base-url=" + url,
                "--agentsquad.worker.model-profiles.default.model-name=test-model",
                "--agentsquad.delivery.poll-delay-ms=100", "--agentsquad.projection.poll-delay-ms=100"};
        String userId = "00000000-0000-0000-0000-000000000001";
        String taskId;
        try {
            try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class, base)) {
                GoalTaskService tasks = context.getBean(GoalTaskService.class);
                var conversation = tasks.createConversation(userId, "多节点写作");
                var task = tasks.submit(userId, conversation.conversationId(), "two-workers", "完成两项写作后汇总");
                taskId = task.taskId();
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                jdbc.update("UPDATE sq_command SET status = 'CANCELLED' WHERE command_id = ?", task.commandId());
                context.getBean(WorkerDefinitionService.class).publish(new AuthView(userId, "tester", List.of(AccountRole.ADMIN), "csrf"),
                        "writer", new WorkerDefinitionCreateRequest("writer", "1.0.0", "撰写局部内容",
                                json.readTree("{}"), json.readTree("{\"type\":\"object\",\"required\":[\"text\"]}"),
                                List.of(), "default", "default"));
                String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id = ?", String.class, taskId);
                ContextSnapshotService snapshots = context.getBean(ContextSnapshotService.class);
                String one = snapshots.prepare(userId, session, "one", "snapshot-one").snapshotId();
                String two = snapshots.prepare(userId, session, "two", "snapshot-two").snapshotId();
                context.getBean(PlanService.class).publish(userId, session,
                        new PlanPublish("two-nodes", 0, List.of(
                                new PlanNode("one", "writer", "1.0.0", "write-node-1", one, List.of(), true, 1),
                                new PlanNode("two", "writer", "1.0.0", "write-node-2", two, parallel ? List.of() : List.of("one"), true, 1)),
                                "ALL_REQUIRED", List.of("两项完成")));
            }
            String[] running = java.util.Arrays.copyOf(base, base.length + 1);
            running[base.length] = "--agentsquad.delivery.enabled=true";
            try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class, running)) {
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                activeDatabase.set(jdbc);
                if (cancel) {
                    assertThat(firstCalls.await(10, TimeUnit.SECONDS)).isTrue();
                    long version = jdbc.queryForObject("SELECT version FROM sq_goal_task WHERE task_id=?", Long.class, taskId);
                    context.getBean(com.agentsquad.squad.conversation.service.TaskControlService.class).cancel(userId, taskId, "stop-both", version);
                    String cancelled = null;
                    for (int attempt = 0; attempt < 150; attempt++) {
                        cancelled = jdbc.queryForObject("SELECT status FROM sq_goal_task WHERE task_id=?", String.class, taskId);
                        if ("CANCELLED".equals(cancelled)) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                    cancelRelease.countDown();
                    assertThat(cancelled).isEqualTo("CANCELLED");
                    assertThat(jdbc.queryForList("SELECT status FROM sq_worker_job WHERE task_id=? ORDER BY id", String.class, taskId))
                            .containsExactly("CANCELLED", "CANCELLED");
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE task_id=? AND action='STOP'", Integer.class, taskId)).isEqualTo(3);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_worker_job WHERE task_id=? AND result_json IS NOT NULL", Integer.class, taskId)).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE task_id=? AND role='ASSISTANT'", Integer.class, taskId)).isZero();
                    assertThat(calls.get()).isEqualTo(2);
                    return;
                }
                String status = null;
                for (int attempt = 0; attempt < 200; attempt++) {
                    status = jdbc.queryForObject("SELECT status FROM sq_goal_task WHERE task_id = ?", String.class, taskId);
                    if ("SUCCEEDED".equals(status) || "PARTIAL".equals(status) || "RECOVERY_BLOCKED".equals(status)) {
                        break;
                    }
                    Thread.sleep(100);
                }
                assertThat(status).isEqualTo(failSecond ? "PARTIAL" : "SUCCEEDED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_worker_job WHERE task_id = ? AND status = 'SUCCEEDED'", Integer.class, taskId)).isEqualTo(failSecond ? 1 : 2);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE task_id = ? AND action = 'MASTER_SIGNAL'", Integer.class, taskId)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_operation WHERE task_id = ?", Integer.class, taskId)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE task_id = ? AND kind = 'MODEL' AND status = 'SETTLED'", Integer.class, taskId)).isEqualTo(6);
                assertThat(calls.get()).isEqualTo(6);
                if (parallel) {
                    assertThat(overlapped.get()).isTrue();
                } else {
                    assertThat(dependencyVisible.get()).isTrue();
                }
            }
        } finally {
            cancelRelease.countDown();
            model.stop(0);
            executor.shutdownNow();
        }
    }

    private String completion(ObjectMapper json, String tool, String arguments) {
        String message = tool == null ? "{\"role\":\"assistant\",\"content\":\"本轮处理完成\"}"
                : "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call-" + tool + "\",\"type\":\"function\",\"function\":{\"name\":\""
                + tool + "\",\"arguments\":" + json.valueToTree(arguments) + "}}]}";
        return """
                {"id":"completion-1","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":%s,"finish_reason":"%s"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                """.formatted(message, tool == null ? "stop" : "tool_calls");
    }
}
