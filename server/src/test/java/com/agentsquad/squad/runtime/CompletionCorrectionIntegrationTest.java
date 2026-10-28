package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.integration.agentscope.NativeEventView;
import com.agentsquad.squad.integration.agentscope.NativeProjectionService;
import com.agentsquad.squad.conversation.domain.*;
import com.agentsquad.squad.runtime.domain.*;
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
 * 使用持久 H2 库和原生事件视图验证一次纠正、再次缺失后的终态，以及取消意图优先级。
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

    private ConfigurableApplicationContext start(Path tempDirectory) {
        String databaseUrl = "jdbc:h2:file:" + tempDirectory.resolve("squad")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        return SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.flyway.enabled=true",
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
