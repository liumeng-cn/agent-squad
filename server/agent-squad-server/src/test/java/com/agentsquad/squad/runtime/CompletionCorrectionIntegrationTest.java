package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.domain.dto.PlanNode;
import com.agentsquad.squad.domain.dto.PlanPublish;
import com.agentsquad.squad.domain.dto.WorkerDefinitionCreateRequest;
import com.agentsquad.squad.domain.entity.*;
import com.agentsquad.squad.integration.agentscope.NativeEventView;
import com.agentsquad.squad.integration.agentscope.NativeProjectionService;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.runtime.repository.*;
import com.agentsquad.squad.runtime.service.CompletionProposalService;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.conversation.service.TaskControlService;
import com.agentsquad.squad.presentation.service.TaskSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Master 完成候选缺失的投影测试。
 * <p>
 * 使用隔离 MySQL 库和原生事件视图验证一次纠正、再次缺失后的终态，以及取消意图优先级。
 */
class CompletionCorrectionIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    /**
     * 同一原生回合的候选重放只保留一份，成功结束后公开答案与目标终态。
     */
    @Test
    void shouldCompleteMatchedProposal(@TempDir Path tempDirectory) {
        try (ConfigurableApplicationContext context = start(tempDirectory)) {
            ProjectionTarget target = createTask(context);
            NativeProjectionService projection = context.getBean(NativeProjectionService.class);
            CompletionProposalService proposals = context.getBean(CompletionProposalService.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            TaskSnapshotService snapshots = context.getBean(TaskSnapshotService.class);
            ConversationMapper conversations = context.getBean(ConversationMapper.class);
            String conversationId = tasks.findTask(USER_ID, target.taskId()).conversationId();
            String turnId = UUID.randomUUID().toString();

            projection.accept(target, event(1, "run/start", turnId, null));
            var first = proposals.propose(USER_ID, target.sessionId(), turnId,
                    "tool-call-1", GoalTaskStatus.SUCCEEDED.name(), "目标已完成");
            var repeated = proposals.propose(USER_ID, target.sessionId(), turnId,
                    "tool-call-1", GoalTaskStatus.SUCCEEDED.name(), "目标已完成");
            projection.accept(target, event(2, "run/end", turnId, NativeRunOutcome.COMPLETED.code()));

            assertThat(repeated).isEqualTo(first);
            assertThat(tasks.findTask(USER_ID, target.taskId()).status()).isEqualTo(GoalTaskStatus.SUCCEEDED.name());
            assertThat(snapshots.snapshot(USER_ID, target.taskId()).answer().text()).isEqualTo("目标已完成");
            assertThat(conversations.findOwned(USER_ID, conversationId).activeTaskId()).isNull();
            assertThat(tasks.listEvents(USER_ID, target.taskId(), 0))
                    .extracting(item -> item.type())
                    .containsExactly("task.accepted", "task.run_started", "task.run_ended",
                            "answer.completed", "task.completed");
        }
    }

    /**
     * 完成候选写入后计划版本变化时，不得发布旧答案；原生结束事件仍须推进水位。
     */
    @Test
    void shouldBlockStaleCompletionCandidate(@TempDir Path tempDirectory) {
        try (ConfigurableApplicationContext context = start(tempDirectory)) {
            ProjectionTarget target = createTask(context);
            NativeProjectionService projection = context.getBean(NativeProjectionService.class);
            CompletionProposalService proposals = context.getBean(CompletionProposalService.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            ProjectionMapper cursors = context.getBean(ProjectionMapper.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String turnId = UUID.randomUUID().toString();

            projection.accept(target, event(1, "run/start", turnId, null));
            proposals.propose(USER_ID, target.sessionId(), turnId,
                    "tool-call-stale", GoalTaskStatus.SUCCEEDED.name(), "旧计划答案");
            jdbc.update("UPDATE sq_goal_task SET plan_version = plan_version + 1 WHERE task_id = ?", target.taskId());

            NativeEventView completed = event(2, "run/end", turnId, NativeRunOutcome.COMPLETED.code());
            projection.accept(target, completed);
            projection.accept(target, completed);

            assertThat(tasks.findTask(USER_ID, target.taskId()).status())
                    .isEqualTo(GoalTaskStatus.RECOVERY_BLOCKED.name());
            assertThat(cursors.lockCursor(USER_ID, target.bindingId())).isEqualTo(2L);
            assertThat(tasks.listEvents(USER_ID, target.taskId(), 0))
                    .extracting(item -> item.type())
                    .containsExactly("task.accepted", "task.run_started", "task.run_ended",
                            "task.recovery_blocked");
        }
    }

    /**
     * 首次缺失只排一次纠正；纠正回合仍缺失才失败并释放对话。
     */
    @Test
    void shouldCorrectOnceThenFailWithoutProposal(@TempDir Path tempDirectory) {
        try (ConfigurableApplicationContext context = start(tempDirectory)) {
            ProjectionTarget target = createTask(context);
            NativeProjectionService projection = context.getBean(NativeProjectionService.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            CommandMapper commands = context.getBean(CommandMapper.class);
            ConversationMapper conversations = context.getBean(ConversationMapper.class);
            ProjectionMapper cursors = context.getBean(ProjectionMapper.class);
            String conversationId = tasks.findTask(USER_ID, target.taskId()).conversationId();
            String firstTurn = UUID.randomUUID().toString();

            projection.accept(target, event(1, "run/start", firstTurn, null));
            projection.accept(target, event(2, "run/end", firstTurn, NativeRunOutcome.COMPLETED.code()));

            assertThat(tasks.findTask(USER_ID, target.taskId()).status()).isEqualTo(GoalTaskStatus.RUNNING.name());
            assertThat(commands.findByKey(USER_ID,
                    "task.completion-correction:" + target.taskId(), firstTurn).status())
                    .isEqualTo(RuntimeCommandStatus.PENDING.name());
            assertThat(conversations.findOwned(USER_ID, conversationId).activeTaskId()).isEqualTo(target.taskId());
            assertThat(cursors.lockCursor(USER_ID, target.bindingId())).isEqualTo(2L);

            String secondTurn = UUID.randomUUID().toString();
            projection.accept(target, event(3, "run/start", secondTurn, null));
            projection.accept(target, event(4, "run/end", secondTurn, NativeRunOutcome.COMPLETED.code()));

            assertThat(tasks.findTask(USER_ID, target.taskId()).status()).isEqualTo(GoalTaskStatus.FAILED.name());
            assertThat(context.getBean(org.springframework.jdbc.core.JdbcTemplate.class).queryForObject(
                    "SELECT category FROM sq_execution_failure WHERE binding_id=?", String.class, target.bindingId())).isEqualTo("LOGIC_INVALID");
            assertThat(conversations.findOwned(USER_ID, conversationId).activeTaskId()).isNull();
            assertThat(tasks.listEvents(USER_ID, target.taskId(), 0))
                    .extracting(item -> item.type())
                    .containsExactly("task.accepted", "task.run_started", "task.run_ended",
                            "task.run_started", "task.run_ended", "answer.completed", "task.completed");
            assertThat(cursors.lockCursor(USER_ID, target.bindingId())).isEqualTo(4L);
        }
    }

    /**
     * 用户取消先于回合结束时，即使原生状态为 completed 也不能继续纠正或宣布成功。
     */
    @Test
    void shouldHonorCancellationWhenNativeRunCompletes(@TempDir Path tempDirectory) {
        try (ConfigurableApplicationContext context = start(tempDirectory)) {
            ProjectionTarget target = createTask(context);
            NativeProjectionService projection = context.getBean(NativeProjectionService.class);
            TaskMapper taskMapper = context.getBean(TaskMapper.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            ConversationMapper conversations = context.getBean(ConversationMapper.class);
            String conversationId = tasks.findTask(USER_ID, target.taskId()).conversationId();
            String turnId = UUID.randomUUID().toString();

            projection.accept(target, event(1, "run/start", turnId, null));
            assertThat(taskMapper.updateCancelling(USER_ID, target.taskId(), 1,
                    LocalDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
            projection.accept(target, event(2, "run/end", turnId, NativeRunOutcome.COMPLETED.code()));

            assertThat(tasks.findTask(USER_ID, target.taskId()).status()).isEqualTo(GoalTaskStatus.CANCELLED.name());
            assertThat(conversations.findOwned(USER_ID, conversationId).activeTaskId()).isNull();
            assertThat(tasks.listEvents(USER_ID, target.taskId(), 0))
                    .extracting(item -> item.type())
                    .containsExactly("task.accepted", "task.run_started", "task.run_ended", "task.completed");
        }
    }

    /**
     * 原生失败不会把用户目标误判为失败终态；旧候选失效后可显式受理恢复。
     */
    @Test
    void shouldBlockFailedRunAndKeepTaskRecoverable(@TempDir Path tempDirectory) {
        try (ConfigurableApplicationContext context = start(tempDirectory)) {
            ProjectionTarget target = createTask(context);
            NativeProjectionService projection = context.getBean(NativeProjectionService.class);
            CompletionProposalService proposals = context.getBean(CompletionProposalService.class);
            CompletionMapper completions = context.getBean(CompletionMapper.class);
            TaskControlService controls = context.getBean(TaskControlService.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            ConversationMapper conversations = context.getBean(ConversationMapper.class);
            String conversationId = tasks.findTask(USER_ID, target.taskId()).conversationId();
            String turnId = UUID.randomUUID().toString();

            projection.accept(target, event(1, "run/start", turnId, null));
            proposals.propose(USER_ID, target.sessionId(), turnId,
                    "tool-call-before-failure", GoalTaskStatus.SUCCEEDED.name(), "旧候选");
            NativeEventView failed = event(2, "run/end", turnId, NativeRunOutcome.FAILED.code());
            projection.accept(target, failed);
            projection.accept(target, failed);

            var blocked = tasks.findTask(USER_ID, target.taskId());
            assertThat(blocked.status()).isEqualTo(GoalTaskStatus.RECOVERY_BLOCKED.name());
            assertThat(blocked.blockedReason()).contains("原生回合执行失败");
            assertThat(completions.lockTask(USER_ID, target.taskId()).proposalJson()).isNull();
            assertThat(conversations.findOwned(USER_ID, conversationId).activeTaskId()).isEqualTo(target.taskId());
            assertThat(tasks.listEvents(USER_ID, target.taskId(), 0))
                    .extracting(item -> item.type())
                    .containsExactly("task.accepted", "task.run_started", "task.run_ended",
                            "task.recovery_blocked");

            var resume = controls.resume(USER_ID, target.taskId(), UUID.randomUUID().toString(), blocked.version());
            assertThat(resume.status()).isEqualTo(RuntimeCommandStatus.PENDING.name());
            assertThat(tasks.findTask(USER_ID, target.taskId()).status()).isEqualTo(GoalTaskStatus.QUEUED.name());
            assertThat(tasks.findTask(USER_ID, target.taskId()).blockedReason()).isNull();

            DeliveryMapper delivery = context.getBean(DeliveryMapper.class);
            String token = UUID.randomUUID().toString();
            LocalDateTime claimedAt = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1);
            assertThat(delivery.claim(resume.commandId(), token, claimedAt,
                    claimedAt.plusSeconds(60))).isEqualTo(1);
            controls.blockRejectedResume(USER_ID, target.taskId(), resume.commandId(),
                    token, "NATIVE_TURN_SETTLED");
            controls.blockRejectedResume(USER_ID, target.taskId(), resume.commandId(),
                    token, "NATIVE_TURN_SETTLED");

            assertThat(tasks.findCommand(USER_ID, resume.commandId()).status())
                    .isEqualTo(RuntimeCommandStatus.BLOCKED.name());
            assertThat(tasks.findTask(USER_ID, target.taskId()).status())
                    .isEqualTo(GoalTaskStatus.RECOVERY_BLOCKED.name());
            assertThat(tasks.listEvents(USER_ID, target.taskId(), 0))
                    .extracting(item -> item.type())
                    .containsExactly("task.accepted", "task.run_started", "task.run_ended",
                            "task.recovery_blocked", "task.recovery_blocked");
        }
    }

    /**
     * 取消意图先于原生失败时，不能覆盖为恢复阻塞。
     */
    @Test
    void shouldCancelInsteadOfBlockingFailedRun(@TempDir Path tempDirectory) {
        try (ConfigurableApplicationContext context = start(tempDirectory)) {
            ProjectionTarget target = createTask(context);
            NativeProjectionService projection = context.getBean(NativeProjectionService.class);
            TaskMapper taskMapper = context.getBean(TaskMapper.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            String turnId = UUID.randomUUID().toString();

            projection.accept(target, event(1, "run/start", turnId, null));
            assertThat(taskMapper.updateCancelling(USER_ID, target.taskId(), 1,
                    LocalDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
            projection.accept(target, event(2, "run/end", turnId, NativeRunOutcome.FAILED.code()));

            assertThat(tasks.findTask(USER_ID, target.taskId()).status()).isEqualTo(GoalTaskStatus.CANCELLED.name());
            assertThat(tasks.listEvents(USER_ID, target.taskId(), 0))
                    .extracting(item -> item.type())
                    .doesNotContain("task.recovery_blocked");
        }
    }

    @Test
    void cancellationWaitsForEveryWriterAndKeepsUnknownOperations(@TempDir Path directory) throws Exception {
        try (var context = start(directory)) {
            var target = createTask(context);
            var jdbc = context.getBean(JdbcTemplate.class);
            var bindings = context.getBean(BindingMapper.class);
            String workerBinding = UUID.randomUUID().toString();
            String workerAgent = UUID.randomUUID().toString().replace("-", "");
            String workerSession = UUID.randomUUID().toString();
            bindings.insertWorker(workerBinding, USER_ID, target.taskId(), workerAgent, workerSession, LocalDateTime.now(ZoneOffset.UTC));
            var store = context.getBean(com.agentsquad.squad.integration.agentscope.journal.RuntimeSessionProvider.class).logStore();
            var runtime = io.agentscope.core.agent.RuntimeContext.builder().userId(USER_ID).sessionId(workerSession).build();
            var log = store.open(new io.agentscope.core.session.SessionKey(USER_ID, workerAgent, workerSession), runtime);
            var writer = log.acquire("other-node", java.time.Duration.ofSeconds(30));
            var operation = context.getBean(com.agentsquad.squad.tools.operation.OperationService.class).prepare(
                    new com.agentsquad.squad.tools.operation.PrepareRequest(USER_ID, target.sessionId(), "turn", "write",
                            "mock_ticket_create", "v1", "WRITE", java.util.Map.of("title", "test"), "stable", false, null, 0, 1, "tester"));
            var unstarted = context.getBean(com.agentsquad.squad.tools.operation.OperationService.class).prepare(
                    new com.agentsquad.squad.tools.operation.PrepareRequest(USER_ID, target.sessionId(), "turn", "write-not-started",
                            "mock_ticket_create", "v1", "WRITE", java.util.Map.of("title", "next"), "another", false, null, 0, 1, "tester"));
            var interaction = context.getBean(com.agentsquad.squad.interaction.service.InteractionService.class).bindFrameworkRequest(
                    USER_ID, target.taskId(), target.bindingId(), null, "turn", "input-request",
                    java.util.Map.of("name", "request_input", "input", java.util.Map.of("question", "请确认要求")));
            jdbc.update("UPDATE sq_operation SET status='UNKNOWN' WHERE operation_id=?", operation.operationId());
            var control = context.getBean(TaskControlService.class);
            var receipt = control.cancel(USER_ID, target.taskId(), "stop-all", 0);
            assertThat(control.cancel(USER_ID, target.taskId(), "stop-all", 0)).isEqualTo(receipt);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> control.cancel(USER_ID, target.taskId(), "another-stop", 1))
                    .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeBusinessException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, unstarted.operationId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT status FROM sq_budget_reservation WHERE user_id=? AND usage_key=?", String.class,
                    USER_ID, unstarted.budgetUsageKey())).isEqualTo("SETTLED");
            assertThat(jdbc.queryForObject("SELECT settled_units FROM sq_budget_reservation WHERE user_id=? AND usage_key=?", Long.class,
                    USER_ID, unstarted.budgetUsageKey())).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM sq_interaction WHERE interaction_id=?", String.class, interaction.interactionId())).isEqualTo("CANCELLED");
            var conversation = context.getBean(ConversationMapper.class).findOwned(USER_ID,
                    context.getBean(GoalTaskService.class).findTask(USER_ID, target.taskId()).conversationId());
            context.getBean(com.agentsquad.squad.conversation.service.ConversationDeletionService.class)
                    .delete(USER_ID, conversation.conversationId(), "delete-while-stopping", conversation.version());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE task_id=? AND action='STOP' AND status='CANCELLED'", Long.class, target.taskId())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE task_id=? AND action='STOP'", Long.class, target.taskId())).isEqualTo(2);
            var finalizer = context.getBean(com.agentsquad.squad.runtime.service.TaskFinalizerService.class);
            finalizer.finalizeCancelledTask(USER_ID, target.taskId());
            assertThat(context.getBean(TaskMapper.class).findOwned(USER_ID, target.taskId()).status()).isEqualTo("CANCELLING");
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, operation.operationId())).isEqualTo("UNKNOWN");
            jdbc.update("UPDATE sq_operation SET status='SUCCEEDED' WHERE operation_id=?", operation.operationId());
            finalizer.finalizeCancelledTask(USER_ID, target.taskId());
            assertThat(context.getBean(TaskMapper.class).findOwned(USER_ID, target.taskId()).status()).isEqualTo("CANCELLING");
            jdbc.update("UPDATE sq_operation SET status='UNKNOWN' WHERE operation_id=?", operation.operationId());
            String workerStop = jdbc.queryForObject("SELECT command_id FROM sq_command WHERE binding_id=? AND action='STOP'", String.class, workerBinding);
            context.getBean(com.agentsquad.squad.integration.agentscope.CommandDeliveryService.class).deliver(workerStop);
            assertThat(context.getBean(CommandMapper.class).findOwned(USER_ID, workerStop).nativeStatus()).isEqualTo("no-active-run");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_worker_job WHERE task_id=?", Long.class, target.taskId())).isZero();
            log.release(writer);
            finalizer.finalizeCancelledTask(USER_ID, target.taskId());
            assertThat(context.getBean(TaskMapper.class).findOwned(USER_ID, target.taskId()).status()).isEqualTo("CANCELLING");
            // 夹具模拟核对完成；生产环境必须由操作账本的核对服务提供该事实。
            jdbc.update("UPDATE sq_operation SET status='SUCCEEDED' WHERE operation_id=?", operation.operationId());
            String sandbox = UUID.randomUUID().toString();
            String scopeId = jdbc.queryForObject("SELECT scope_id FROM sq_goal_task WHERE task_id=?", String.class, target.taskId());
            jdbc.update("INSERT INTO sq_sandbox_execution (execution_id,user_id,scope_id,binding_id,logical_key,turn_id,tool_call_id,request_hash,status,deadline_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    sandbox, USER_ID, scopeId, target.bindingId(), "sandbox", "turn", "sandbox-call", "a".repeat(64), "UNKNOWN", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
            finalizer.finalizeCancelledTask(USER_ID, target.taskId());
            assertThat(context.getBean(TaskMapper.class).findOwned(USER_ID, target.taskId()).status()).isEqualTo("CANCELLING");
            assertThat(jdbc.queryForObject("SELECT status FROM sq_sandbox_execution WHERE execution_id=?", String.class, sandbox)).isEqualTo("UNKNOWN");
            jdbc.update("UPDATE sq_sandbox_execution SET status='SUCCEEDED' WHERE execution_id=?", sandbox);
            context.getBean(com.agentsquad.squad.runtime.service.CancellationSettlementScheduler.class).settle();
            assertThat(context.getBean(TaskMapper.class).findOwned(USER_ID, target.taskId()).status()).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, operation.operationId())).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_worker_job WHERE task_id=?", Long.class, target.taskId())).isZero();
        }
    }

    @Test
    void cancellationFencesAndStopsEveryJobWithoutStartingNewWork(@TempDir Path directory) throws Exception {
        try (var context = start(directory)) {
            var target = createTask(context);
            var jdbc = context.getBean(JdbcTemplate.class);
            var json = context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
            String key = "writer-" + UUID.randomUUID();
            context.getBean(com.agentsquad.squad.worker.service.WorkerDefinitionService.class).publish(
                    new com.agentsquad.squad.identity.AuthView(USER_ID, "admin", java.util.List.of(com.agentsquad.squad.common.enums.identity.AccountRole.ADMIN), null), key,
                    new WorkerDefinitionCreateRequest(key, "1.0.0", "写作", json.readTree("{}"), json.readTree("{}"), java.util.List.of(), "default", "default"));
            var snapshots = context.getBean(com.agentsquad.squad.runtime.service.ContextSnapshotService.class);
            String one = snapshots.prepare(USER_ID, target.sessionId(), "one", "one").snapshotId();
            String two = snapshots.prepare(USER_ID, target.sessionId(), "two", "two").snapshotId();
            var plan = context.getBean(com.agentsquad.squad.runtime.service.PlanService.class).publish(USER_ID, target.sessionId(),
                    new PlanPublish("plan", 0, java.util.List.of(
                            new PlanNode("one", key, "1.0.0", "分析一", one, java.util.List.of(), true, 1),
                            new PlanNode("two", key, "1.0.0", "分析二", two, java.util.List.of(), true, 1)), "ALL_REQUIRED", java.util.List.of("完成")));
            String firstBinding = jdbc.queryForObject("SELECT binding_id FROM sq_worker_job WHERE job_id=?", String.class, plan.jobIds().getFirst());
            String secondBinding = jdbc.queryForObject("SELECT binding_id FROM sq_worker_job WHERE job_id=?", String.class, plan.jobIds().getLast());
            var dispatch = context.getBean(com.agentsquad.squad.runtime.service.WorkerDispatchService.class);
            var claimed = dispatch.claim(USER_ID, firstBinding);
            long version = context.getBean(TaskMapper.class).findOwned(USER_ID, target.taskId()).version();
            context.getBean(TaskControlService.class).cancel(USER_ID, target.taskId(), "stop-jobs", version);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatch.renew(USER_ID, firstBinding, claimed.guardToken()))
                    .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeBusinessException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatch.claim(USER_ID, secondBinding))
                    .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeBusinessException.class);
            var stops = jdbc.queryForList("SELECT command_id FROM sq_command WHERE task_id=? AND action='STOP' ORDER BY id", String.class, target.taskId());
            assertThat(stops).hasSize(3);
            var delivery = context.getBean(com.agentsquad.squad.integration.agentscope.CommandDeliveryService.class);
            for (String command : stops) {
                delivery.deliver(command);
                assertThat(context.getBean(CommandMapper.class).findOwned(USER_ID, command).nativeStatus()).isEqualTo("no-active-run");
            }
            assertThat(context.getBean(TaskMapper.class).findOwned(USER_ID, target.taskId()).status()).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForList("SELECT status FROM sq_worker_job WHERE task_id=? ORDER BY id", String.class, target.taskId()))
                    .containsExactly("CANCELLED", "CANCELLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE user_id=?", Long.class, USER_ID)).isZero();
            assertThat(context.getBean(GoalTaskService.class).listEvents(USER_ID, target.taskId(), 0).stream()
                    .filter(event -> event.type().equals("task.completed"))).hasSize(1);
        }
    }

    private ConfigurableApplicationContext start(Path tempDirectory) {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("squad"));
        return SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=true",
                "--agentsquad.delivery.poll-delay-ms=3600000",
                "--agentsquad.projection.poll-delay-ms=3600000",
                "--agentsquad.model.api-key=test-key",
                "--agentsquad.model.base-url=https://example.invalid/v1",
                "--agentsquad.model.name=test-model");
    }

    private ProjectionTarget createTask(ConfigurableApplicationContext context) {
        ConversationMapper conversations = context.getBean(ConversationMapper.class);
        TaskMapper tasks = context.getBean(TaskMapper.class);
        BindingMapper bindings = context.getBean(BindingMapper.class);
        TaskEventMapper events = context.getBean(TaskEventMapper.class);
        String conversationId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String bindingId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        conversations.insert(conversationId, USER_ID, "候选纠正");
        tasks.insert(taskId, USER_ID, conversationId, "{\"message\":\"请回答\"}", 5000000L);
        context.getBean(ExecutionScopeService.class).createTaskScope(USER_ID, taskId, conversationId, 0, "STANDARD", 5000000L);
        bindings.insertMaster(bindingId, USER_ID, taskId, MasterAgentIdentity.AGENT_ID, sessionId, now);
        conversations.activateTask(USER_ID, conversationId, taskId);
        events.insertAccepted(USER_ID, taskId, "command:" + UUID.randomUUID(),
                "{\"taskId\":\"" + taskId + "\"}", now);
        return new ProjectionTarget(USER_ID, taskId, bindingId, MasterAgentIdentity.AGENT_ID, sessionId, 0);
    }

    private NativeEventView event(long sequence, String type, String turnId, String outcome) {
        return new NativeEventView(sequence, UUID.randomUUID().toString(), type,
                UUID.randomUUID().toString(), turnId, outcome, System.currentTimeMillis(),
                java.util.Map.of());
    }
}
