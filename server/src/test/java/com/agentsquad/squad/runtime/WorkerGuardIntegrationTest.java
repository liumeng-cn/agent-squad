package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.runtime.domain.WorkerLaunchRow;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.dto.PlanReceipt;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import com.agentsquad.squad.runtime.service.PlanService;
import com.agentsquad.squad.runtime.service.WorkerDispatchService;
import com.agentsquad.squad.runtime.service.WorkerResultService;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Worker 节点 guard 与结果候选的持久竞争测试。
 *
 * 仅验证平台事务边界；真实 AgentScope writer lease 需另做原生运行实验。
 */
class WorkerGuardIntegrationTest {

    @Test
    void onlyOneGuardCanRunAndCandidateNeedsMatchingTurn(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext spring = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("squad")
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.username=sa",
                "--spring.datasource.driver-class-name=org.h2.Driver")) {
            String userId = "00000000-0000-0000-0000-000000000001";
            GoalTaskService tasks = spring.getBean(GoalTaskService.class);
            ContextSnapshotService snapshots = spring.getBean(ContextSnapshotService.class);
            PlanService plans = spring.getBean(PlanService.class);
            WorkerDefinitionService definitions = spring.getBean(WorkerDefinitionService.class);
            WorkerDispatchService dispatch = spring.getBean(WorkerDispatchService.class);
            WorkerResultService results = spring.getBean(WorkerResultService.class);
            ObjectMapper json = spring.getBean(ObjectMapper.class);
            JdbcTemplate jdbc = spring.getBean(JdbcTemplate.class);

            ConversationResponse conversation = tasks.createConversation(userId, "Worker guard");
            TaskReceipt task = tasks.submit(userId, conversation.conversationId(),
                    "worker-guard", "写一段摘要");
            definitions.publish(new AuthView(userId, "admin", List.of("ADMIN"), "csrf"),
                    "guard-definition", new WorkerDefinitionCreateRequest(
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
            PlanReceipt plan = plans.publish(userId, masterSession,
                    new PlanPublish("guard-plan", 0, List.of(
                            new PlanNode("write", "writer", "1.0.0", "写摘要",
                                    snapshotId, List.of(), true, 1)),
                            "ALL_REQUIRED", List.of("文字完成")));
            String bindingId = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, plan.jobIds().getFirst());

            WorkerLaunchRow claimed = dispatch.claim(userId, bindingId);
            assertThat(claimed.jobStatus()).isEqualTo("RUNNING");
            assertThatThrownBy(() -> dispatch.claim(userId, bindingId))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> results.propose(userId, bindingId, "old-guard",
                    "turn-1", json.readTree("{\"text\":\"结果\"}")))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> results.propose(userId, bindingId, claimed.guardToken(),
                    "turn-1", json.readTree("{}")))
                    .isInstanceOf(RuntimeBusinessException.class);

            String jobId = results.propose(userId, bindingId, claimed.guardToken(),
                    "turn-1", json.readTree("{\"text\":\"结果\"}"));
            assertThat(jobId).isEqualTo(plan.jobIds().getFirst());
            assertThat(results.finalizeSuccess(userId, bindingId, "turn-old")).isFalse();
            assertThat(results.finalizeSuccess(userId, bindingId, "turn-1")).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM sq_worker_job WHERE job_id = ?",
                    String.class, jobId)).isEqualTo("SUCCEEDED");
        }
    }
}
