package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.domain.dto.ConversationResponse;
import com.agentsquad.squad.domain.response.TaskReceiptResponse;
import com.agentsquad.squad.service.GoalTaskService;
import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.integration.agentscope.middleware.ScopedMasterToolsMiddleware;
import com.agentsquad.squad.domain.dto.PlanNode;
import com.agentsquad.squad.domain.dto.PlanPublish;
import com.agentsquad.squad.domain.dto.PlanReceipt;
import com.agentsquad.squad.service.ContextSnapshotService;
import com.agentsquad.squad.service.MasterSignalService;
import com.agentsquad.squad.service.PlanService;
import com.agentsquad.squad.service.WorkerDispatchService;
import com.agentsquad.squad.service.WorkerResultService;
import com.agentsquad.squad.service.TaskSnapshotService;
import com.agentsquad.squad.domain.request.WorkerDefinitionCreateRequest;
import com.agentsquad.squad.service.WorkerDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import com.agentsquad.squad.common.enums.identity.AccountRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * publish_plan 的事务与 DAG 校验测试。
 *
 * <p>验证计划与节点一次落库、根节点 READY、同 logicalKey 重放、循环依赖与能力版本校验。</p>
 */
class PlanPublishIntegrationTest {

    @Test
    void shouldReuseMatchingSerialResults(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("reuse"));
        String userId = "00000000-0000-0000-0000-000000000001";
        try (ConfigurableApplicationContext context = SpringApplication.run(
                AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver")) {
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            WorkerDefinitionService definitions = context.getBean(WorkerDefinitionService.class);
            ContextSnapshotService snapshots = context.getBean(ContextSnapshotService.class);
            PlanService plans = context.getBean(PlanService.class);
            WorkerDispatchService dispatch = context.getBean(WorkerDispatchService.class);
            WorkerResultService results = context.getBean(WorkerResultService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ObjectMapper json = context.getBean(ObjectMapper.class);
            ConversationResponse conversation = tasks.createConversation(userId, "复用测试");
            TaskReceiptResponse task = tasks.submit(userId, conversation.conversationId(),
                    "reuse-goal", "写一段摘要");
            definitions.publish(new AuthView(userId, "admin", List.of(AccountRole.ADMIN), "csrf"),
                    "reuse-writer", new WorkerDefinitionCreateRequest(
                            "reuse-writer", "1.0.0", "写摘要", json.readTree("{}"),
                            json.readTree("{}"), List.of(), "default", "default"));
            String sessionId = jdbc.queryForObject("""
                    SELECT framework_session_id FROM sq_runtime_binding
                    WHERE task_id = ? AND role = 'MASTER'
                    """, String.class, task.taskId());
            String snapshot = snapshots.prepare(userId, sessionId,
                    "draft", "reuse-context").snapshotId();
            String finishSnapshot = snapshots.prepare(userId, sessionId,
                    "finish", "finish-context").snapshotId();
            PlanReceipt first = plans.publish(userId, sessionId,
                    new PlanPublish("first-plan", 0, List.of(
                            new PlanNode("draft", "reuse-writer", "1.0.0", "写摘要",
                                    snapshot, List.of(), true, 1),
                            new PlanNode("finish", "reuse-writer", "1.0.0", "整理摘要",
                                    finishSnapshot, List.of("draft"), true, 1)),
                            "ALL_REQUIRED", List.of("摘要完成")));
            String firstBinding = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, first.jobIds().getFirst());
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED' WHERE binding_id = ?",
                    firstBinding);
            String guard = dispatch.claim(userId, firstBinding).guardToken();
            results.propose(userId, firstBinding, guard, "draft-turn",
                    json.readTree("{\"text\":\"已写摘要\"}"));
            assertThat(results.finalizeSuccess(userId, firstBinding, "draft-turn")).isTrue();
            String finishBinding = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, first.jobIds().get(1));
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED' WHERE binding_id = ?",
                    finishBinding);
            String finishGuard = dispatch.claim(userId, finishBinding).guardToken();
            results.propose(userId, finishBinding, finishGuard, "finish-turn",
                    json.readTree("{\"text\":\"已整理摘要\"}"));
            assertThat(results.finalizeSuccess(userId, finishBinding, "finish-turn")).isTrue();

            PlanReceipt reused = plans.publish(userId, sessionId,
                    new PlanPublish("second-plan", 1, List.of(
                            new PlanNode("draft", "reuse-writer", "1.0.0", "写摘要",
                                    snapshot, List.of(), true, 1),
                            new PlanNode("finish", "reuse-writer", "1.0.0", "整理摘要",
                                    finishSnapshot, List.of("draft"), true, 1)),
                            "ALL_REQUIRED", List.of("摘要完成")));
            for (int index = 0; index < 2; index++) {
                String reusedId = reused.jobIds().get(index);
                assertThat(jdbc.queryForObject("""
                        SELECT status FROM sq_worker_job WHERE job_id = ?
                        """, String.class, reusedId)).isEqualTo("SUCCEEDED");
                assertThat(jdbc.queryForObject("""
                        SELECT reused_from_job_id FROM sq_worker_job WHERE job_id = ?
                        """, String.class, reusedId)).isEqualTo(first.jobIds().get(index));
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM sq_command WHERE scope_key = ?
                        """, Integer.class, "worker-job:" + reusedId)).isZero();
            }
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sq_task_event WHERE task_id = ? AND event_type = 'job.reused'
                    """, Integer.class, task.taskId())).isEqualTo(2);

            PlanReceipt changedGoal = plans.publish(userId, sessionId,
                    new PlanPublish("third-plan", 2, List.of(
                            new PlanNode("draft", "reuse-writer", "1.0.0", "换一个摘要目标",
                                    snapshot, List.of(), true, 1)),
                            "ALL_REQUIRED", List.of("新摘要完成")));
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM sq_worker_job WHERE job_id = ?
                    """, String.class, changedGoal.jobIds().getFirst())).isEqualTo("READY");
            String changedBinding = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, changedGoal.jobIds().getFirst());
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED' WHERE binding_id = ?",
                    changedBinding);
            String changedGuard = dispatch.claim(userId, changedBinding).guardToken();
            results.propose(userId, changedBinding, changedGuard, "changed-turn",
                    json.readTree("{\"text\":\"新内容\",\"evidenceRefs\":[\"external\"]}"));
            assertThat(results.finalizeSuccess(userId, changedBinding, "changed-turn")).isTrue();

            PlanReceipt externalEvidence = plans.publish(userId, sessionId,
                    new PlanPublish("fourth-plan", 3, List.of(
                            new PlanNode("draft", "reuse-writer", "1.0.0", "换一个摘要目标",
                                    snapshot, List.of(), true, 1)),
                            "ALL_REQUIRED", List.of("新摘要完成")));
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM sq_worker_job WHERE job_id = ?
                    """, String.class, externalEvidence.jobIds().getFirst())).isEqualTo("READY");
            assertThat(jdbc.queryForObject("""
                    SELECT reused_from_job_id FROM sq_worker_job WHERE job_id = ?
                    """, String.class, externalEvidence.jobIds().getFirst())).isNull();
        }
    }

    @Test
    void shouldPublishPlanAndRejectInvalidDag(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("squad"));
        String[] properties = {
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        };
        String userId = "00000000-0000-0000-0000-000000000001";

        try (ConfigurableApplicationContext context =
                     SpringApplication.run(AgentSquadApplication.class, properties)) {
            GoalTaskService goalTaskService = context.getBean(GoalTaskService.class);
            WorkerDefinitionService workerDefinitionService = context.getBean(WorkerDefinitionService.class);
            PlanService planService = context.getBean(PlanService.class);
            WorkerDispatchService dispatchService = context.getBean(WorkerDispatchService.class);
            WorkerResultService resultService = context.getBean(WorkerResultService.class);
            TaskSnapshotService snapshotsView = context.getBean(TaskSnapshotService.class);
            MasterSignalService signalService = context.getBean(MasterSignalService.class);
            ContextSnapshotService contextSnapshotService = context.getBean(ContextSnapshotService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            ConversationResponse conversation = goalTaskService.createConversation(userId, "排障");
            TaskReceiptResponse task = goalTaskService.submit(userId, conversation.conversationId(),
                    "request-1", "排查错误日志");

            AuthView admin = new AuthView(userId, "admin", List.of(AccountRole.ADMIN), "csrf");
            workerDefinitionService.publish(admin, "wd-1", new WorkerDefinitionCreateRequest(
                    "log-analysis", "1.0.0", "日志分析",
                    mapper.readTree("{}"), mapper.readTree("{}"),
                    List.of(), "default", "default"));
            workerDefinitionService.publish(admin, "wd-schema", new WorkerDefinitionCreateRequest(
                    "structured-input", "1.0.0", "要求结构化主题",
                    mapper.readTree("""
                            {"type":"object","required":["topic"],
                             "properties":{"topic":{"type":"string"}}}
                            """), mapper.readTree("{}"),
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

            PlanPublish missingContext = new PlanPublish("missing-context", 0, List.of(
                    new PlanNode("analyze", "log-analysis", "1.0.0", "分析日志",
                            "00000000-0000-0000-0000-000000000099", List.of(), true, 1)),
                    "ALL_REQUIRED", List.of("完成"));
            assertThatThrownBy(() -> planService.publish(userId, sessionId, missingContext))
                    .isInstanceOf(RuntimeBusinessException.class)
                    .hasMessageContaining("局部上下文");

            PlanPublish publish = new PlanPublish("plan-1", 0, List.of(
                    new PlanNode("analyze", "log-analysis", "1.0.0", "分析日志",
                            analyzeContext, List.of(), true, 1),
                    new PlanNode("report", "log-analysis", "1.0.0", "输出报告",
                            reportContext, List.of("analyze"), true, 1)),
                    "ALL_REQUIRED", List.of("报告完成"));

            jdbc.update("UPDATE sq_budget_account SET limit_microunits = 100 WHERE account_id = (SELECT s.account_id FROM sq_execution_scope s JOIN sq_goal_task t ON t.user_id = s.user_id AND t.scope_id = s.scope_id WHERE t.task_id = ?)",
                    task.taskId());
            assertThatThrownBy(() -> planService.publish(userId, sessionId, publish))
                    .isInstanceOf(RuntimeBusinessException.class)
                    .hasMessageContaining("预算不足");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sq_plan WHERE task_id = ?", Integer.class,
                    task.taskId())).isZero();
            jdbc.update("UPDATE sq_budget_account SET limit_microunits = 5000000 WHERE account_id = (SELECT s.account_id FROM sq_execution_scope s JOIN sq_goal_task t ON t.user_id = s.user_id AND t.scope_id = s.scope_id WHERE t.task_id = ?)",
                    task.taskId());

            PlanReceipt receipt = planService.publish(userId, sessionId, publish);
            assertThat(receipt.planVersion()).isEqualTo(1);
            assertThat(receipt.jobIds()).hasSize(2);
            jdbc.update("""
                    UPDATE sq_command SET status = 'DELIVERED', native_turn_id = 'master-plan-turn'
                    WHERE command_id = ?
                    """, task.commandId());

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

            String rootBinding = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, receipt.jobIds().getFirst());
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED' WHERE binding_id = ?",
                    rootBinding);
            String rootGuard = dispatchService.claim(userId, rootBinding).guardToken();
            resultService.propose(userId, rootBinding, rootGuard, "root-turn",
                    mapper.readTree("{\"text\":\"分析完成\"}"));
            assertThat(resultService.finalizeSuccess(userId, rootBinding, "root-turn")).isTrue();
            assertThat(snapshotsView.snapshot(userId, task.taskId()).jobs())
                    .hasSize(2)
                    .anySatisfy(job -> {
                        assertThat(job.status()).isEqualTo(com.agentsquad.squad.common.enums.worker.WorkerJobStatus.SUCCEEDED);
                        assertThat(job.result().toString()).contains("分析完成");
                    });
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM sq_worker_job WHERE job_id = ?",
                    String.class, receipt.jobIds().get(1))).isEqualTo("READY");
            assertThat(jdbc.queryForObject("""
                    SELECT c.payload_json FROM sq_command c
                    JOIN sq_worker_job j ON j.binding_id = c.binding_id
                    WHERE j.job_id = ? AND c.action = 'SUBMIT'
                    """, String.class, receipt.jobIds().get(1))).contains("分析完成");

            String reportBinding = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, receipt.jobIds().get(1));
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED' WHERE binding_id = ?",
                    reportBinding);
            String reportGuard = dispatchService.claim(userId, reportBinding).guardToken();
            resultService.propose(userId, reportBinding, reportGuard, "report-turn",
                    mapper.readTree("{\"text\":\"报告完成\"}"));
            assertThat(resultService.finalizeSuccess(userId, reportBinding, "report-turn")).isTrue();
            assertThat(resultService.finalizeSuccess(userId, reportBinding, "report-turn")).isFalse();
            assertThat(jdbc.queryForObject("""
                    SELECT signal_version FROM sq_plan WHERE task_id = ? AND plan_version = 1
                    """, Long.class, task.taskId())).isEqualTo(2L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sq_command WHERE task_id = ? AND action = 'MASTER_SIGNAL'
                    """, Integer.class, task.taskId())).isZero();
            signalService.onMasterTurnEnded(userId, task.taskId(), "master-plan-turn");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sq_command WHERE task_id = ? AND action = 'MASTER_SIGNAL'
                      AND status = 'PENDING'
                    """, Integer.class, task.taskId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT payload_json FROM sq_command WHERE task_id = ? AND action = 'MASTER_SIGNAL'
                    """, String.class, task.taskId())).contains("分析完成", "报告完成");

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
                    new PlanNode("summary", "structured-input", "1.0.0", "topic：日志分析；请汇总结果",
                            summaryContext, List.of(), true, 1)),
                    "ALL_REQUIRED", List.of("汇总完成"));
            PlanReceipt secondReceipt = planService.publish(userId, sessionId, second);
            assertThat(secondReceipt.planVersion()).isEqualTo(2);

            // 旧版汇总回合发布了新版计划时，只等待新版工作，不排入完成纠正。
            jdbc.update("UPDATE sq_command SET native_turn_id = 'old-plan-signal' WHERE task_id = ? AND action = 'MASTER_SIGNAL'", task.taskId());
            var commands = context.getBean(com.agentsquad.squad.conversation.repository.CommandMapper.class);
            assertThat(commands.countPlanSignalTurn(userId, task.taskId(), "old-plan-signal", 1)).isEqualTo(1);
            assertThat(commands.countPlanSignalTurn(userId, task.taskId(), "old-plan-signal", 2)).isZero();
            jdbc.update("UPDATE sq_goal_task SET status = 'RUNNING' WHERE task_id = ?", task.taskId());
            String masterBinding = jdbc.queryForObject("SELECT binding_id FROM sq_runtime_binding WHERE framework_session_id = ?", String.class, sessionId);
            var finalizer = new com.agentsquad.squad.service.TaskFinalizerService();
            org.springframework.test.util.ReflectionTestUtils.setField(finalizer, "directives", context.getBean(com.agentsquad.squad.conversation.repository.TaskDirectiveMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(finalizer, "completionMapper", context.getBean(com.agentsquad.squad.runtime.repository.CompletionMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(finalizer, "planMapper", context.getBean(com.agentsquad.squad.runtime.repository.PlanMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(finalizer, "commandMapper", commands);
            var transaction = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            assertThat(transaction.<Boolean>execute(status -> finalizer.recoverMissingProposal(
                    new ProjectionTargetDO(userId, task.taskId(), masterBinding, "master", sessionId, 0),
                    new com.agentsquad.squad.integration.agentscope.NativeEventView(100, "old-end", "run/end", "old-run", "old-plan-signal", "completed", 1000, java.util.Map.of()))))
                    .isTrue();
            assertThat(jdbc.queryForObject("SELECT completion_correction_count FROM sq_goal_task WHERE task_id = ?", Integer.class, task.taskId())).isZero();

            // 实际执行时等待，恢复受阻时开放协调工具，不能同时要求 Master 空等。
            var middleware = new ScopedMasterToolsMiddleware();
            org.springframework.test.util.ReflectionTestUtils.setField(middleware, "scopes", context.getBean(com.agentsquad.squad.service.ExecutionScopeService.class));
            org.springframework.test.util.ReflectionTestUtils.setField(middleware, "scopeMapper", context.getBean(com.agentsquad.squad.conversation.repository.ExecutionScopeMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(middleware, "tasks", context.getBean(com.agentsquad.squad.conversation.repository.TaskMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(middleware, "plans", context.getBean(com.agentsquad.squad.runtime.repository.PlanMapper.class));
            for (String state : List.of("READY", "RECOVERY_BLOCKED")) {
                jdbc.update("UPDATE sq_worker_job SET status = ? WHERE job_id = ?", state, secondReceipt.jobIds().getFirst());
                middleware.onModelCall(null, io.agentscope.core.agent.RuntimeContext.builder().userId(userId).sessionId(sessionId).build(),
                        new io.agentscope.core.middleware.ModelCallInput(List.of(), List.of(io.agentscope.core.model.ToolSchema.builder().name("propose_completion").description("完成").build()), null, null), adjusted -> {
                            assertThat(adjusted.tools().isEmpty()).isEqualTo("READY".equals(state));
                            if ("RECOVERY_BLOCKED".equals(state)) {
                                assertThat(mapper.valueToTree(adjusted.messages()).toString()).contains("不能继续空等", "完整替换当前安排");
                            }
                            return reactor.core.publisher.Flux.empty();
                        }).blockLast();
            }
            jdbc.update("UPDATE sq_worker_job SET status = 'READY' WHERE job_id = ?", secondReceipt.jobIds().getFirst());

            String oldPlanStatus = jdbc.queryForObject(
                    "SELECT status FROM sq_plan WHERE task_id = ? AND plan_version = 1",
                    String.class, task.taskId());
            assertThat(oldPlanStatus).isEqualTo("SUPERSEDED");

            List<String> oldJobStatuses = jdbc.queryForList(
                    "SELECT status FROM sq_worker_job WHERE task_id = ? AND plan_version = 1",
                    String.class, task.taskId());
            assertThat(oldJobStatuses).containsExactlyInAnyOrder("SUCCEEDED", "SUCCEEDED");
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM sq_command WHERE task_id = ? AND action = 'MASTER_SIGNAL'
                    """, String.class, task.taskId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sq_command c
                    JOIN sq_worker_job j ON j.binding_id = c.binding_id
                    WHERE j.task_id = ? AND j.plan_version = 1 AND c.status = 'CANCELLED'
                    """, Integer.class, task.taskId())).isZero();

            String secondBinding = jdbc.queryForObject(
                    "SELECT binding_id FROM sq_worker_job WHERE job_id = ?",
                    String.class, secondReceipt.jobIds().getFirst());
            jdbc.update("UPDATE sq_command SET status = 'DELIVERED' WHERE binding_id = ?",
                    secondBinding);
            String secondGuard = dispatchService.claim(userId, secondBinding).guardToken();
            resultService.propose(userId, secondBinding, secondGuard, "summary-turn",
                    mapper.readTree("{\"text\":\"汇总完成\"}"));
            assertThat(resultService.finalizeSuccess(userId, secondBinding, "summary-turn")).isTrue();
            assertThat(jdbc.queryForObject("""
                    SELECT signal_version FROM sq_plan WHERE task_id = ? AND plan_version = 2
                    """, Long.class, task.taskId())).isEqualTo(3L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sq_command WHERE task_id = ? AND action = 'MASTER_SIGNAL'
                      AND status = 'PENDING'
                    """, Integer.class, task.taskId())).isEqualTo(1);
        }
    }
}
