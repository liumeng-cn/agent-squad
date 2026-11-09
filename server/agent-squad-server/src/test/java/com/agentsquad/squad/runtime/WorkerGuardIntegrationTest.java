package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.model.bo.*;
import com.agentsquad.squad.model.dto.WorkerDefinitionCreateDTO;
import com.agentsquad.squad.model.vo.ConversationVO;
import com.agentsquad.squad.model.vo.TaskReceiptVO;
import com.agentsquad.squad.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Worker 节点 guard 与结果候选的持久竞争测试。
 * <p>
 * 仅验证平台事务边界；真实 AgentScope writer lease 需另做原生运行实验。
 */
class WorkerGuardIntegrationTest {

    @Test
    void onlyOneGuardCanRunAndCandidateNeedsMatchingTurn(@TempDir Path directory) throws Exception {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("squad"));
        try (ConfigurableApplicationContext spring = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver")) {
            String userId = "00000000-0000-0000-0000-000000000001";
            GoalTaskService tasks = spring.getBean(GoalTaskService.class);
            ContextSnapshotService snapshots = spring.getBean(ContextSnapshotService.class);
            PlanService plans = spring.getBean(PlanService.class);
            WorkerDefinitionService definitions = spring.getBean(WorkerDefinitionService.class);
            WorkerDispatchService dispatch = spring.getBean(WorkerDispatchService.class);
            WorkerResultService results = spring.getBean(WorkerResultService.class);
            ObjectMapper json = spring.getBean(ObjectMapper.class);
            JdbcTemplate jdbc = spring.getBean(JdbcTemplate.class);

            ConversationVO conversation = tasks.createConversation(userId, "Worker guard");
            TaskReceiptVO task = tasks.submit(userId, conversation.conversationId(),
                    "worker-guard", "写一段摘要");
            definitions.publish(new AuthView(userId, "admin", List.of(AccountRole.ADMIN), "csrf"),
                    "guard-definition", new WorkerDefinitionCreateDTO(
                            "writer", "1.0.0", "写摘要",
                            json.readTree("{}"),
                            json.readTree("{\"type\":\"object\",\"required\":[\"text\"],"
                                    + "\"properties\":{\"text\":{\"type\":\"string\"}}}"),
                            List.of(), "default", "default"));
            String masterSession = jdbc.queryForObject("""
                    SELECT framework_session_id FROM sq_runtime_binding
                    WHERE task_id = ? AND role = 'MASTER'
                    """, String.class, task.taskId());
            String snapshotId = snapshots.prepare(userId, masterSession,
                    "write", "guard-snapshot").snapshotId();
            PlanReceiptBO plan = plans.publish(userId, masterSession,
                    new PlanPublishBO("guard-plan", 0, List.of(
                            new PlanNodeBO("write", "writer", "1.0.0", "写摘要",
                                    snapshotId, List.of(), true, 1)),
                            "ALL_REQUIRED", List.of("文字完成")));
            String bindingId = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, plan.jobIds().getFirst());

            WorkerLaunchBO claimed = dispatch.claim(userId, bindingId);
            assertThat(claimed.getJobStatus()).isEqualTo("RUNNING");
            assertThatThrownBy(() -> dispatch.claim(userId, bindingId))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> results.propose(userId, bindingId, "old-guard",
                    "turn-1", json.readTree("{\"text\":\"结果\"}")))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> results.propose(userId, bindingId, claimed.getGuardToken(),
                    "turn-1", json.readTree("{}")))
                    .isInstanceOf(RuntimeBusinessException.class);

            String jobId = results.propose(userId, bindingId, claimed.getGuardToken(),
                    "turn-1", json.readTree("{\"text\":\"结果\"}"));
            assertThat(jobId).isEqualTo(plan.jobIds().getFirst());
            assertThat(results.finalizeSuccess(userId, bindingId, "turn-old")).isFalse();
            assertThat(results.finalizeSuccess(userId, bindingId, "turn-1")).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM sq_worker_job WHERE job_id = ?",
                    String.class, jobId)).isEqualTo("SUCCEEDED");
            String finishSnapshot = snapshots.prepare(userId, masterSession,
                    "finish", "finish-snapshot").snapshotId();

            PlanReceiptBO oldPlan = plans.publish(userId, masterSession,
                    new PlanPublishBO("in-flight-plan", 1, List.of(
                            new PlanNodeBO("write", "writer", "1.0.0", "写新摘要",
                                    snapshotId, List.of(), true, 1)),
                            "ALL_REQUIRED", List.of("文字完成")));
            String oldJobId = oldPlan.jobIds().getFirst();
            String oldBindingId = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, oldJobId);
            WorkerLaunchBO oldWriter = dispatch.claim(userId, oldBindingId);
            results.propose(userId, oldBindingId, oldWriter.getGuardToken(), "old-turn",
                    json.readTree("{\"text\":\"旧结果与已发生的副作用证据\"}"));
            try (ConfigurableApplicationContext peer = SpringApplication.run(AgentSquadApplication.class,
                    "--spring.main.web-application-type=none",
                    "--spring.datasource.url=" + databaseUrl,
                    "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                    "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver")) {
                WorkerDispatchService peerDispatch = peer.getBean(WorkerDispatchService.class);
                WorkerResultService peerResults = peer.getBean(WorkerResultService.class);
                assertThatThrownBy(() -> peerDispatch.claim(userId, oldBindingId))
                        .isInstanceOf(RuntimeBusinessException.class);
                jdbc.update("UPDATE sq_worker_job SET guard_lease_until = ? WHERE job_id = ?",
                        LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), oldJobId);
                WorkerLaunchBO newWriter = peerDispatch.claim(userId, oldBindingId);
                assertThat(newWriter.getGuardToken()).isNotEqualTo(oldWriter.getGuardToken());
                assertThatThrownBy(() -> results.propose(userId, oldBindingId,
                        oldWriter.getGuardToken(), "stale-turn",
                        json.readTree("{\"text\":\"旧实例迟到\"}")))
                        .isInstanceOf(RuntimeBusinessException.class);
                peerResults.propose(userId, oldBindingId, newWriter.getGuardToken(), "old-turn",
                        json.readTree("{\"text\":\"旧结果与已发生的副作用证据\"}"));
                jdbc.update("""
                        UPDATE sq_command SET status = 'DELIVERED', native_turn_id = 'old-turn'
                        WHERE binding_id = ? AND action = 'SUBMIT'
                        """, oldBindingId);
                WorkerRecoveryService recovery = peer.getBean(WorkerRecoveryService.class);
                String oldCommandId = jdbc.queryForObject("""
                        SELECT command_id FROM sq_command
                        WHERE binding_id = ? AND action = 'SUBMIT'
                        """, String.class, oldBindingId);
                String workerSession = jdbc.queryForObject("""
                        SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id = ?
                        """, String.class, oldBindingId);
                ProjectionTargetBO target = new ProjectionTargetBO(userId, task.taskId(), oldBindingId,
                        newWriter.getAgentId(), workerSession, 0);
                assertThat(recovery.originalSubmitCommandId(target)).isEqualTo(oldCommandId);
                NativeSubmissionReceiptBO interrupted = new NativeSubmissionReceiptBO(
                        oldCommandId, "old-turn", "old-run", "interrupted");
                recovery.queueInterrupted(target, interrupted);
                recovery.queueInterrupted(target, interrupted);
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM sq_command
                        WHERE binding_id = ? AND action = 'RESUME'
                        """, Integer.class, oldBindingId)).isEqualTo(1);
            }
            String operationId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO sq_operation
                        (operation_id, user_id, task_id, job_id, binding_id,
                         framework_turn_id, tool_call_id, tool_key, tool_version,
                         effect, request_hash, arguments_json, status, result_json)
                    VALUES (?, ?, ?, ?, ?, 'old-turn', 'old-call', 'mock-write', '1.0.0',
                            'WRITE', 'digest', '{}', 'SUCCEEDED', '{"externalId":"test"}')
                    """, operationId, userId, task.taskId(), oldJobId, oldBindingId);

            PlanReceiptBO newPlan = plans.publish(userId, masterSession,
                    new PlanPublishBO("replacement-plan", 2, List.of(
                            new PlanNodeBO("write", "writer", "1.0.0", "重新写摘要",
                                    snapshotId, List.of(), true, 1),
                            new PlanNodeBO("finish", "writer", "1.0.0", "合并结果",
                                    finishSnapshot, List.of("write"), true, 1)),
                            "ALL_REQUIRED", List.of("文字完成")));
            assertThat(newPlan.planVersion()).isEqualTo(3);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM sq_worker_job WHERE job_id = ?",
                    String.class, oldJobId)).isEqualTo("SUPERSEDED");
            assertThat(jdbc.queryForObject(
                    "SELECT result_candidate_json FROM sq_worker_job WHERE job_id = ?",
                    String.class, oldJobId)).contains("旧结果");
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM sq_operation WHERE operation_id = ?",
                    String.class, operationId)).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject(
                    "SELECT cancel_requested_at IS NOT NULL FROM sq_worker_job WHERE job_id = ?",
                    Boolean.class, oldJobId)).isTrue();
            assertThat(results.finalizeSuccess(userId, oldBindingId, "old-turn")).isFalse();
            assertThatThrownBy(() -> results.propose(userId, oldBindingId,
                    oldWriter.getGuardToken(), "late-turn", json.readTree("{\"text\":\"迟到结果\"}")))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM sq_worker_job WHERE job_id = ?",
                    String.class, newPlan.jobIds().getFirst())).isEqualTo("READY");
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM sq_worker_job WHERE job_id = ?",
                    String.class, newPlan.jobIds().get(1))).isEqualTo("BLOCKED");

            String limitedJob = newPlan.jobIds().getFirst();
            String limitedBinding = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?", String.class, limitedJob);
            WorkerLaunchBO firstGuard = dispatch.claim(userId, limitedBinding);
            String limitedCommand = jdbc.queryForObject("""
                    SELECT command_id FROM sq_command WHERE binding_id = ? AND action = 'SUBMIT'
                    """, String.class, limitedBinding);
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED', native_turn_id = 'limited-turn' WHERE command_id = ?",
                    limitedCommand);
            String limitedSession = jdbc.queryForObject(
                    "SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id = ?",
                    String.class, limitedBinding);
            ProjectionTargetBO limitedTarget = new ProjectionTargetBO(userId, task.taskId(), limitedBinding,
                    firstGuard.getAgentId(), limitedSession, 0);
            NativeSubmissionReceiptBO limitedReceipt = new NativeSubmissionReceiptBO(
                    limitedCommand, "limited-turn", "limited-run", "interrupted");
            WorkerRecoveryService recovery = spring.getBean(WorkerRecoveryService.class);
            recovery.queueInterrupted(limitedTarget, limitedReceipt);
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED' WHERE binding_id = ? AND action = 'RESUME'",
                    limitedBinding);
            jdbc.update("UPDATE sq_worker_job SET guard_lease_until = ? WHERE job_id = ?",
                    LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), limitedJob);
            dispatch.claim(userId, limitedBinding);
            recovery.queueInterrupted(limitedTarget, limitedReceipt);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_worker_job WHERE job_id = ?",
                    String.class, limitedJob)).isEqualTo("RECOVERY_BLOCKED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE binding_id = ? AND action = 'RESUME'",
                    Integer.class, limitedBinding)).isEqualTo(1);
        }
    }
}
