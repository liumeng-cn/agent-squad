package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.domain.enums.AccountRole;
import com.agentsquad.squad.domain.enums.WorkerJobStatus;
import com.agentsquad.squad.domain.enums.WorkerSkipReason;
import com.agentsquad.squad.service.GoalTaskService;
import com.agentsquad.squad.domain.dto.AbandonFailedJobRequest;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.service.TaskSnapshotService;
import com.agentsquad.squad.domain.dto.PlanNode;
import com.agentsquad.squad.domain.dto.PlanPublish;
import com.agentsquad.squad.service.ContextSnapshotService;
import com.agentsquad.squad.service.PlanService;
import com.agentsquad.squad.service.WorkerDispatchService;
import com.agentsquad.squad.service.WorkerResultService;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
import com.agentsquad.squad.domain.request.WorkerDefinitionCreateRequest;
import com.agentsquad.squad.service.WorkerDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 依赖失败的事务与公开步骤验证
 * <p>
 * 使用隔离MySQL和真实业务服务确认链式跳过、重复处理幂等及恢复受限仍然等待，不运行真实模型。
 */
class WorkerDependencyFailureIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void closesOnlyPermanentlyFailedDescendants(boolean permanentFailure, @TempDir Path directory) throws Exception {
        String user = "00000000-0000-0000-0000-000000000001";
        try (var context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=" + ("true".equals(System.getenv("SQUAD_STEPS_BROWSER_PROBE")) ? "servlet" : "none"),
                "--server.port=0", "--server.shutdown=immediate", "--agentsquad.identity.allow-unsigned-local=true",
                "--spring.datasource.url=" + MySqlTestDatabase.url(directory),
                "--spring.datasource.username=" + MySqlTestDatabase.username(), "--spring.datasource.password=" + MySqlTestDatabase.password())) {
            var tasks = context.getBean(GoalTaskService.class);
            var conversation = tasks.createConversation(user, "失败步骤验证");
            var task = tasks.submit(user, conversation.conversationId(), "dependency-failure", "完成文章整理");
            var jdbc = context.getBean(JdbcTemplate.class);
            var json = context.getBean(ObjectMapper.class);
            context.getBean(WorkerDefinitionService.class).publish(new AuthView(user, "test", List.of(AccountRole.ADMIN), "csrf"),
                    "steps-writer", new WorkerDefinitionCreateRequest("steps-writer", "1.0.0", "整理文字",
                            json.readTree("{}"), json.readTree("{}"), List.of(), "default", "default"));
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
            var snapshots = context.getBean(ContextSnapshotService.class);
            var nodes = new ArrayList<PlanNode>();
            for (String key : List.of("root", "middle", "leaf", "parallel")) {
                String snapshot = snapshots.prepare(user, session, key, "context-" + key).snapshotId();
                String title = switch (key) {
                    case "root" -> "读取资料";
                    case "middle" -> "整理资料";
                    case "leaf" -> "生成文章";
                    default -> "检查格式";
                };
                nodes.add(new PlanNode(key, "steps-writer", "1.0.0", title, snapshot,
                        key.equals("middle") ? List.of("root") : key.equals("leaf") ? List.of("middle") : List.of(), true, 1));
            }
            context.getBean(PlanService.class).publish(user, session,
                    new PlanPublish("plan", 0, nodes, "ALL_REQUIRED", List.of("文章整理完成")));
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_worker_job WHERE node_key='root'", String.class);
            jdbc.update("UPDATE sq_command SET status='DELIVERED' WHERE binding_id=?", binding);
            context.getBean(WorkerDispatchService.class).claim(user, binding);
            var results = context.getBean(WorkerResultService.class);
            var resolutions = context.getBean(com.agentsquad.squad.service.WorkerFailureResolutionService.class);
            String rootJob = jdbc.queryForObject("SELECT job_id FROM sq_worker_job WHERE node_key='root'", String.class);
            var decision = new AbandonFailedJobRequest(rootJob, 1);
            assertThatThrownBy(() -> resolutions.abandon(user, session, decision))
                    .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeBusinessException.class);
            if (!permanentFailure) {
                results.markRecoveryBlocked(user, binding);
                var waiting = context.getBean(TaskSnapshotService.class).listJobs(user, task.taskId()).items();
                assertThat(waiting.stream().filter(job -> job.nodeKey().equals("middle") || job.nodeKey().equals("leaf")))
                        .allMatch(job -> job.status() == WorkerJobStatus.BLOCKED && job.skipReason() == null);
                assertThat(context.getBean(PlanMapper.class).countActiveJobs(user, task.taskId(), 1)).isEqualTo(4);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_task_event WHERE task_id=? AND event_type='job.skipped'", Integer.class, task.taskId())).isZero();
                assertThatThrownBy(() -> resolutions.abandon(user, session,
                        new AbandonFailedJobRequest(rootJob, 2)))
                        .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeBusinessException.class);
                jdbc.update("""
                        INSERT INTO sq_interaction (interaction_id,user_id,task_id,origin,kind,question,
                            answer_schema_json,request_hash,expires_at,status)
                        VALUES ('pending-test',?,?,'BUSINESS','INPUT','test','{}','test',DATE_ADD(NOW(),INTERVAL 1 HOUR),'OPEN')
                        """, user, task.taskId());
                assertThatThrownBy(() -> resolutions.abandon(user, session, decision))
                        .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeBusinessException.class);
                jdbc.update("UPDATE sq_interaction SET status='CANCELLED' WHERE interaction_id='pending-test'");
                assertThat(resolutions.abandon(user, session, decision).status()).isEqualTo(WorkerJobStatus.FAILED);
                assertThat(resolutions.abandon(user, session, decision).status()).isEqualTo(WorkerJobStatus.FAILED);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_task_event WHERE task_id=? AND event_type='job.failed'", Integer.class, task.taskId())).isEqualTo(1);
            }
            results.markFailed(user, binding);
            results.markFailed(user, binding);
            var jobs = context.getBean(TaskSnapshotService.class).listJobs(user, task.taskId()).items();
            assertThat(jobs.stream().filter(job -> job.status() == WorkerJobStatus.SKIPPED).map(job -> job.nodeKey()).toList())
                    .containsExactly("middle", "leaf");
            assertThat(jobs.stream().filter(job -> job.status() == WorkerJobStatus.SKIPPED))
                    .allMatch(job -> job.skipReason() == WorkerSkipReason.DEPENDENCY_FAILED && job.bindingId() == null);
            assertThat(jobs.stream().filter(job -> job.nodeKey().equals("parallel")).findFirst().orElseThrow().status())
                    .isEqualTo(WorkerJobStatus.READY);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_task_event WHERE task_id=? AND event_type='job.skipped'", Integer.class, task.taskId())).isEqualTo(2);
            assertThat(context.getBean(PlanMapper.class).countActiveJobs(user, task.taskId(), 1)).isEqualTo(1);
            if (permanentFailure && "true".equals(System.getenv("SQUAD_STEPS_BROWSER_PROBE"))) {
                var ready = Path.of("/tmp/squad-browser-probe.json");
                var done = Path.of("/tmp/squad-steps-browser-done");
                java.nio.file.Files.deleteIfExists(done);
                int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context).getWebServer().getPort();
                java.nio.file.Files.writeString(ready, "{\"port\":" + port + ",\"conversationId\":\"" + conversation.conversationId() + "\"}");
                try {
                    for (int attempt = 0; attempt < 3000 && !java.nio.file.Files.exists(done); attempt++) {
                        Thread.sleep(100);
                    }
                    assertThat(java.nio.file.Files.exists(done)).isTrue();
                } finally {
                    java.nio.file.Files.deleteIfExists(ready);
                    java.nio.file.Files.deleteIfExists(done);
                }
            }
            context.getBean(PlanService.class).publish(user, session,
                    new PlanPublish("replacement", 1, nodes, "ALL_REQUIRED", List.of("文章整理完成")));
            assertThatThrownBy(() -> resolutions.abandon(user, session, decision))
                    .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeBusinessException.class);
            results.markFailed(user, binding);
            var replacement = context.getBean(TaskSnapshotService.class).listJobs(user, task.taskId()).items();
            assertThat(replacement).allMatch(job -> job.planVersion() == 2 && job.status() != WorkerJobStatus.SKIPPED);
            assertThat(replacement.stream().filter(job -> job.status() == WorkerJobStatus.READY)).hasSize(2);
        }
    }
}
