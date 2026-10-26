package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.dto.PlanReceipt;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import com.agentsquad.squad.runtime.service.PlanService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.agentsquad.squad.worker.WorkerDefinitionService;
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
 * publish_plan 的事务与 DAG 校验测试。
 *
 * <p>验证计划与节点一次落库、根节点 READY、同 logicalKey 重放、循环依赖与能力版本校验。</p>
 */
class PlanPublishIntegrationTest {

    @Test
    void shouldPublishPlanAndRejectInvalidDag(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = "jdbc:h2:file:" + tempDirectory.resolve("squad")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        String[] properties = {
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.flyway.enabled=true"
        };
        String userId = "00000000-0000-0000-0000-000000000001";

        try (ConfigurableApplicationContext context =
                     SpringApplication.run(AgentSquadApplication.class, properties)) {
            GoalTaskService goalTaskService = context.getBean(GoalTaskService.class);
            WorkerDefinitionService workerDefinitionService = context.getBean(WorkerDefinitionService.class);
            PlanService planService = context.getBean(PlanService.class);
            ContextSnapshotService contextSnapshotService = context.getBean(ContextSnapshotService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            ConversationResponse conversation = goalTaskService.createConversation(userId, "排障");
            TaskReceipt task = goalTaskService.submit(userId, conversation.conversationId(),
                    "request-1", "排查错误日志");

            AuthView admin = new AuthView(userId, "admin", List.of("ADMIN"), "csrf");
            workerDefinitionService.publish(admin, "wd-1", new WorkerDefinitionCreateRequest(
                    "log-analysis", "1.0.0", "日志分析",
                    mapper.readTree("{}"), mapper.readTree("{}"),
                    List.of(), "default", "default"));

            String sessionId = jdbc.queryForObject(
                    "SELECT framework_session_id FROM sq_runtime_binding "
                            + "WHERE user_id = ? AND task_id = ? AND role = 'MASTER'",
                    String.class, userId, task.taskId());
            String analyzeContext = contextSnapshotService.prepare(userId, sessionId,
                    "analyze", "context-analyze").snapshotId();
            assertThat(contextSnapshotService.prepare(userId, sessionId,
                    "analyze", "context-analyze").snapshotId()).isEqualTo(analyzeContext);
            String reportContext = contextSnapshotService.prepare(userId, sessionId,
                    "report", "context-report").snapshotId();
            String summaryContext = contextSnapshotService.prepare(userId, sessionId,
                    "summary", "context-summary").snapshotId();
            assertThatThrownBy(() -> contextSnapshotService.prepare(
                    "00000000-0000-0000-0000-000000000002", sessionId,
                    "analyze", "foreign-context"))
                    .isInstanceOf(RuntimeBusinessException.class);

            PlanPublish publish = new PlanPublish("plan-1", 0, List.of(
                    new PlanNode("analyze", "log-analysis", "1.0.0", "分析日志",
                            analyzeContext, List.of(), true, 1),
                    new PlanNode("report", "log-analysis", "1.0.0", "输出报告",
                            reportContext, List.of("analyze"), true, 1)),
                    "ALL_REQUIRED", List.of("报告完成"));

            jdbc.update("UPDATE sq_goal_task SET budget_limit_microunits = 100 WHERE task_id = ?",
                    task.taskId());
            assertThatThrownBy(() -> planService.publish(userId, sessionId, publish))
                    .isInstanceOf(RuntimeBusinessException.class)
                    .hasMessageContaining("预算不足");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sq_plan WHERE task_id = ?", Integer.class,
                    task.taskId())).isZero();
            jdbc.update("UPDATE sq_goal_task SET budget_limit_microunits = 5000000 WHERE task_id = ?",
                    task.taskId());

            PlanReceipt receipt = planService.publish(userId, sessionId, publish);
            assertThat(receipt.planVersion()).isEqualTo(1);
            assertThat(receipt.jobIds()).hasSize(2);

            List<String> statuses = jdbc.queryForList(
                    "SELECT status FROM sq_worker_job WHERE task_id = ? AND plan_version = 1 "
                            + "ORDER BY node_key",
                    String.class, task.taskId());
            assertThat(statuses).containsExactlyInAnyOrder("READY", "BLOCKED");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sq_command c
                    JOIN sq_worker_job j ON j.binding_id = c.binding_id
                    WHERE j.task_id = ? AND j.plan_version = 1 AND j.status = 'READY'
                      AND c.action = 'SUBMIT' AND c.status = 'PENDING'
                    """, Integer.class, task.taskId())).isEqualTo(1);

            PlanReceipt replay = planService.publish(userId, sessionId, publish);
            assertThat(replay.planId()).isEqualTo(receipt.planId());
            assertThat(replay.jobIds()).isEqualTo(receipt.jobIds());

            PlanPublish reusedKeyWithDifferentGoal = new PlanPublish("plan-1", 0, List.of(
                    new PlanNode("analyze", "log-analysis", "1.0.0", "另一项分析",
                            analyzeContext, List.of(), true, 1)),
                    "ALL_REQUIRED", List.of("报告完成"));
            assertThatThrownBy(() -> planService.publish(userId, sessionId, reusedKeyWithDifferentGoal))
                    .isInstanceOf(RuntimeBusinessException.class)
                    .hasMessageContaining("逻辑键对应的内容不同");

            PlanPublish wrongContext = new PlanPublish("wrong-context", 1, List.of(
                    new PlanNode("analyze", "log-analysis", "1.0.0", "分析日志",
                            reportContext, List.of(), true, 1)),
                    "ALL_REQUIRED", List.of("完成"));
            assertThatThrownBy(() -> planService.publish(userId, sessionId, wrongContext))
                    .isInstanceOf(RuntimeBusinessException.class)
                    .hasMessageContaining("局部上下文");

            PlanPublish cyclic = new PlanPublish("plan-2", 1, List.of(
                    new PlanNode("a", "log-analysis", "1.0.0", "节点A",
                            analyzeContext, List.of("b"), true, 1),
                    new PlanNode("b", "log-analysis", "1.0.0", "节点B",
                            reportContext, List.of("a"), true, 1)),
                    "ALL_REQUIRED", List.of("完成"));
            assertThatThrownBy(() -> planService.publish(userId, sessionId, cyclic))
                    .isInstanceOf(RuntimeBusinessException.class)
                    .hasMessageContaining("循环");

            PlanPublish second = new PlanPublish("plan-2", 1, List.of(
                    new PlanNode("summary", "log-analysis", "1.0.0", "汇总结果",
                            summaryContext, List.of(), true, 1)),
                    "ALL_REQUIRED", List.of("汇总完成"));
            PlanReceipt secondReceipt = planService.publish(userId, sessionId, second);
            assertThat(secondReceipt.planVersion()).isEqualTo(2);

            String oldPlanStatus = jdbc.queryForObject(
                    "SELECT status FROM sq_plan WHERE task_id = ? AND plan_version = 1",
                    String.class, task.taskId());
            assertThat(oldPlanStatus).isEqualTo("SUPERSEDED");

            List<String> oldJobStatuses = jdbc.queryForList(
                    "SELECT status FROM sq_worker_job WHERE task_id = ? AND plan_version = 1",
                    String.class, task.taskId());
            assertThat(oldJobStatuses).containsOnly("SUPERSEDED");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sq_command c
                    JOIN sq_worker_job j ON j.binding_id = c.binding_id
                    WHERE j.task_id = ? AND j.plan_version = 1 AND c.status = 'CANCELLED'
                    """, Integer.class, task.taskId())).isEqualTo(1);
        }
    }
}
