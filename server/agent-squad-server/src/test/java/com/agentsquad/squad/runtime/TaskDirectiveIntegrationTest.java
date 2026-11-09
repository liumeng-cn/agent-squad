package com.agentsquad.squad.runtime;

import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.TaskDirectiveStatus;
import com.agentsquad.squad.enums.TaskDirectiveWaitReason;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.model.bo.*;
import com.agentsquad.squad.model.dto.MessageCreateDTO;
import com.agentsquad.squad.model.dto.WorkerDefinitionCreateDTO;
import com.agentsquad.squad.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用户修改目标的持久受理与版本屏障验证。
 * <p>
 * 使用正式消息、目标、计划及工具账本服务，验证安全点、所有权、幂等与原计划晚到结果的隔离。
 */
class TaskDirectiveIntegrationTest {
    @Test
    void appliesAtSafePointAndFencesPreviousPlan(@TempDir Path directory) throws Exception {
        try (var context = start(directory)) {
            verifyApply(context);
        }
    }

    @Test
    void unknownWriteDefersDirectiveAndPreservesLedger(@TempDir Path directory) throws Exception {
        try (var context = start(directory)) {
            verifyUnknown(context);
        }
    }

    @Test
    void concurrentChangesApplyOnceAndRejectStaleGoal(@TempDir Path directory) throws Exception {
        try (var context = start(directory)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            String user = UUID.randomUUID().toString();
            var messages = context.getBean(MessageAcceptanceService.class);
            var source = messages.accept(user, null, "source", new MessageCreateDTO("写介绍", List.of()));
            var task = context.getBean(GoalTaskCreationService.class).create(user, replySession(jdbc, source.executionId()),
                    new GoalTaskCreateBO("goal", "介绍", "写介绍", source.messageId()));
            String target = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
            var service = context.getBean(TaskDirectiveService.class);
            var first = messages.accept(user, source.conversationId(), "first", new MessageCreateDTO("改成活泼风格", List.of()));
            var second = messages.accept(user, source.conversationId(), "second", new MessageCreateDTO("改成正式风格", List.of()));
            long version = version(jdbc, task.taskId());
            var one = service.submit(user, replySession(jdbc, first.executionId()), new TaskDirectiveSubmitBO("one", task.taskId(), version, "SUPPLEMENT", "活泼"));
            var two = service.submit(user, replySession(jdbc, second.executionId()), new TaskDirectiveSubmitBO("two", task.taskId(), version, "SUPPLEMENT", "正式"));
            var query = context.getBean(com.agentsquad.squad.service.ConversationQueryService.class);
            Long commands = jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE user_id=?", Long.class, user);
            query.snapshot(user, source.conversationId(), null, 40);
            query.snapshot(user, source.conversationId(), null, 40);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE user_id=?", Long.class, user)).isEqualTo(commands);
            var start = new java.util.concurrent.CountDownLatch(1);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> {
                    start.await();
                    return service.decide(user, target, new TaskDirectiveDecisionBO(one.directiveId(), version, "APPLY", null));
                });
                var b = pool.submit(() -> {
                    start.await();
                    return service.decide(user, target, new TaskDirectiveDecisionBO(two.directiveId(), version, "APPLY", null));
                });
                start.countDown();
                assertThat(List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS).status(), b.get(10, java.util.concurrent.TimeUnit.SECONDS).status()))
                        .containsExactlyInAnyOrder("APPLIED", "REJECTED");
            }
            var changes = query.snapshot(user, source.conversationId(), null, 40).directives();
            assertThat(changes).hasSize(2);
            assertThat(changes).filteredOn(item -> item.status() == TaskDirectiveStatus.REJECTED)
                    .singleElement().satisfies(item -> assertThat(item.reasonCode()).isEqualTo("GOAL_CHANGED"));
            assertThat(jdbc.queryForObject("SELECT goal_revision FROM sq_goal_task WHERE task_id=?", Long.class, task.taskId())).isEqualTo(1);
        }
    }

    @Test
    void cancelledTaskRejectsPendingChangeInsteadOfApplyingIt(@TempDir Path directory) {
        try (var context = start(directory)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            String user = UUID.randomUUID().toString();
            var messages = context.getBean(MessageAcceptanceService.class);
            var source = messages.accept(user, null, "source", new MessageCreateDTO("写介绍", List.of()));
            var task = context.getBean(GoalTaskCreationService.class).create(user, replySession(jdbc, source.executionId()),
                    new GoalTaskCreateBO("goal", "介绍", "写介绍", source.messageId()));
            var change = messages.accept(user, source.conversationId(), "change", new MessageCreateDTO("改成活泼风格", List.of()));
            long version = version(jdbc, task.taskId());
            var service = context.getBean(TaskDirectiveService.class);
            var receipt = service.submit(user, replySession(jdbc, change.executionId()), new TaskDirectiveSubmitBO("change", task.taskId(), version, "SUPPLEMENT", "活泼"));
            context.getBean(com.agentsquad.squad.service.TaskControlService.class).cancel(user, task.taskId(), "stop", version);
            service.dispatch(new TaskDirectiveTargetBO(receipt.directiveId(), user, task.taskId()));
            var snapshot = context.getBean(com.agentsquad.squad.service.ConversationQueryService.class)
                    .snapshot(user, source.conversationId(), null, 40);
            assertThat(snapshot.directives()).singleElement().satisfies(item -> {
                assertThat(item.status()).isEqualTo(TaskDirectiveStatus.REJECTED);
                assertThat(item.reasonCode()).isEqualTo("TASK_ENDED");
            });
            assertThat(jdbc.queryForObject("SELECT goal_revision FROM sq_goal_task WHERE task_id=?", Long.class, task.taskId())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE task_id=? AND action='TASK_DIRECTIVE'", Long.class, task.taskId())).isZero();
        }
    }

    static void verifyApply(ConfigurableApplicationContext context) throws Exception {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String user = UUID.randomUUID().toString();
        var messages = context.getBean(MessageAcceptanceService.class);
        var source = messages.accept(user, null, "source", new MessageCreateDTO("写一段正式介绍", List.of()));
        var task = context.getBean(GoalTaskCreationService.class).create(user, replySession(jdbc, source.executionId()),
                new GoalTaskCreateBO("goal", "介绍", "写介绍", source.messageId()));
        String targetSession = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
        ObjectMapper json = context.getBean(ObjectMapper.class);
        context.getBean(WorkerDefinitionService.class).publish(new AuthView(user, "admin", List.of(AccountRole.ADMIN), "csrf"), "writer-" + user,
                new WorkerDefinitionCreateDTO("writer-" + user, "1.0.0", "写作", json.readTree("{}"), json.readTree("{}"), List.of(), "default", "default"));
        var snapshots = context.getBean(ContextSnapshotService.class);
        String snapshot = snapshots.prepare(user, targetSession, "draft", "before").snapshotId();
        var plans = context.getBean(PlanService.class);
        var planRequest = new PlanPublishBO("original", 0, List.of(new PlanNodeBO("draft", "writer-" + user, "1.0.0", "介绍", snapshot, List.of(), true, 1)), "ALL_REQUIRED", List.of("完成"));
        var original = plans.publish(user, targetSession, planRequest);
        String oldBinding = jdbc.queryForObject("SELECT binding_id FROM sq_worker_job WHERE job_id=?", String.class, original.jobIds().getFirst());
        jdbc.update("UPDATE sq_command SET status='DELIVERED' WHERE binding_id=?", oldBinding);
        String guard = context.getBean(WorkerDispatchService.class).claim(user, oldBinding).getGuardToken();
        var supplement = messages.accept(user, source.conversationId(), "supplement", new MessageCreateDTO("改成活泼风格，突出合作", List.of()));
        String session = replySession(jdbc, supplement.executionId());
        long version = version(jdbc, task.taskId());
        var directives = context.getBean(TaskDirectiveService.class);
        var request = new TaskDirectiveSubmitBO("supplement", task.taskId(), version, "SUPPLEMENT", "加入活泼风格与合作重点");
        var receipt = directives.submit(user, session, request);
        assertThat(receipt.status()).isEqualTo("ACCEPTED");
        assertThat(receipt.appliedVersion()).isNull();
        var query = context.getBean(com.agentsquad.squad.service.ConversationQueryService.class);
        var acceptedSnapshot = query.snapshot(user, source.conversationId(), null, 40);
        assertThat(acceptedSnapshot.directives()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(TaskDirectiveStatus.ACCEPTED);
            assertThat(item.waitReason()).isEqualTo(TaskDirectiveWaitReason.WAITING_SAFE_POINT);
            assertThat(item.sourceMessageId()).isEqualTo(supplement.messageId());
        });
        assertThatThrownBy(() -> query.snapshot(UUID.randomUUID().toString(), source.conversationId(), null, 40))
                .isInstanceOf(RuntimeBusinessException.class);
        assertThat(directives.submit(user, session, request)).isEqualTo(receipt);
        assertThatThrownBy(() -> directives.submit(user, session, new TaskDirectiveSubmitBO("supplement", task.taskId(), version, "REPLACE", "替换"))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> directives.decide(user, session, new TaskDirectiveDecisionBO(receipt.directiveId(), version, "APPLY", null))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> directives.submit(UUID.randomUUID().toString(), session, request)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> plans.publish(user, targetSession, planRequest)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> directives.decide(user, targetSession, new TaskDirectiveDecisionBO(receipt.directiveId(), version + 1, "APPLY", null))).isInstanceOf(RuntimeBusinessException.class);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> tx.execute(status -> {
            directives.decide(user, targetSession, new TaskDirectiveDecisionBO(receipt.directiveId(), version, "APPLY", null));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM sq_task_directive WHERE directive_id=?", String.class, receipt.directiveId())).isEqualTo("ACCEPTED");
        var applied = directives.decide(user, targetSession, new TaskDirectiveDecisionBO(receipt.directiveId(), version, "APPLY", null));
        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(applied.goalRevision()).isEqualTo(1);
        assertThat(query.snapshot(user, source.conversationId(), null, 40).directives()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo(TaskDirectiveStatus.APPLIED));
        assertThat(query.snapshot(user, source.conversationId(), jdbc.queryForObject("SELECT message_seq FROM sq_message WHERE message_id=?", Long.class, supplement.messageId()), 40).directives()).isEmpty();
        assertThat(directives.decide(user, targetSession, new TaskDirectiveDecisionBO(receipt.directiveId(), version, "APPLY", null))).isEqualTo(applied);
        assertThat(jdbc.queryForObject("SELECT status FROM sq_worker_job WHERE job_id=?", String.class, original.jobIds().getFirst())).isEqualTo("SUPERSEDED");
        assertThatThrownBy(() -> context.getBean(WorkerResultService.class).propose(user, oldBinding, guard, "late-turn", json.readTree("{}"))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> snapshots.prepare(user, targetSession, "draft", "before")).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> plans.publish(user, targetSession, planRequest)).isInstanceOf(RuntimeBusinessException.class);
        String after = snapshots.prepare(user, targetSession, "draft", "after").snapshotId();
        String items = jdbc.queryForObject("SELECT items_json FROM sq_context_snapshot WHERE snapshot_id=?", String.class, after);
        assertThat(items).contains("USER_SUPPLEMENT", "改成活泼风格，突出合作").doesNotContain("加入活泼风格与合作重点");
        var next = plans.publish(user, targetSession, new PlanPublishBO("updated", 1, List.of(new PlanNodeBO("draft", "writer-" + user, "1.0.0", "新介绍", after, List.of(), true, 1)), "ALL_REQUIRED", List.of("完成")));
        assertThat(next.planVersion()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT goal_revision FROM sq_plan WHERE plan_id=?", Long.class, next.planId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account WHERE user_id=?", Long.class, user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT input_json FROM sq_goal_task WHERE task_id=?", String.class, task.taskId())).contains("写一段正式介绍");
    }

    static void verifyUnknown(ConfigurableApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String user = UUID.randomUUID().toString();
        var messages = context.getBean(MessageAcceptanceService.class);
        var source = messages.accept(user, null, "source", new MessageCreateDTO("完成工作", List.of()));
        var task = context.getBean(GoalTaskCreationService.class).create(user, replySession(jdbc, source.executionId()), new GoalTaskCreateBO("goal", "工作", "完成工作", source.messageId()));
        String target = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
        var operations = context.getBean(OperationService.class);
        var write = new PrepareRequestBO(user, target, "turn", "write", "mock_ticket_create", "v1", "WRITE", Map.of("title", "test"), "stable", false, null, 0, 1, "tester");
        var operation = operations.prepare(write);
        jdbc.update("UPDATE sq_operation SET status='UNKNOWN' WHERE operation_id=?", operation.getOperationId());
        var message = messages.accept(user, source.conversationId(), "change", new MessageCreateDTO("改成另一项工作", List.of()));
        var service = context.getBean(TaskDirectiveService.class);
        var receipt = service.submit(user, replySession(jdbc, message.executionId()), new TaskDirectiveSubmitBO("change", task.taskId(), version(jdbc, task.taskId()), "REPLACE", "另一工作"));
        jdbc.update("UPDATE sq_command SET status='APPLIED' WHERE task_id=?", task.taskId());
        service.dispatch(new TaskDirectiveTargetBO(receipt.directiveId(), user, task.taskId()));
        assertThat(jdbc.queryForObject("SELECT command_id FROM sq_task_directive WHERE directive_id=?", String.class, receipt.directiveId())).isNull();
        var query = context.getBean(com.agentsquad.squad.service.ConversationQueryService.class);
        var waiting = query.snapshot(user, source.conversationId(), null, 40);
        assertThat(waiting.directives()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(TaskDirectiveStatus.ACCEPTED);
            assertThat(item.waitReason()).isEqualTo(TaskDirectiveWaitReason.WAITING_OPERATION);
        });
        service.dispatch(new TaskDirectiveTargetBO(receipt.directiveId(), user, task.taskId()));
        assertThat(query.snapshot(user, source.conversationId(), null, 40).cursor()).isEqualTo(waiting.cursor());
        assertThat(query.events(user, source.conversationId(), null)).filteredOn(event -> event.type().equals("directive.status_changed"))
                .allSatisfy(event -> {
                    assertThat(event.payload().path("schemaVersion").asInt()).isEqualTo(2);
                    assertThat(event.payload().path("directive").has("sourceText")).isFalse();
                    assertThat(event.payload().path("directive").has("commandId")).isFalse();
                });
        assertThatThrownBy(() -> service.decide(user, target, new TaskDirectiveDecisionBO(receipt.directiveId(), version(jdbc, task.taskId()), "APPLY", null))).isInstanceOf(RuntimeBusinessException.class);
        assertThat(operations.prepare(write).getOperationId()).isEqualTo(operation.getOperationId());
        assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, operation.getOperationId())).isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> operations.prepare(new PrepareRequestBO(user, target, "turn", "write-new", "mock_ticket_create", "v1", "WRITE", Map.of("title", "new"), "new", false, null, 0, 1, "tester"))).isInstanceOf(RuntimeBusinessException.class);
        assertThat(service.decide(user, target, new TaskDirectiveDecisionBO(receipt.directiveId(), version(jdbc, task.taskId()), "REJECT", "原操作结果尚未确认，请先处理原工作")).status()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT goal_revision FROM sq_goal_task WHERE task_id=?", Long.class, task.taskId())).isZero();
    }

    private static long version(JdbcTemplate jdbc, String task) {
        return jdbc.queryForObject("SELECT version FROM sq_goal_task WHERE task_id=?", Long.class, task);
    }

    private static String replySession(JdbcTemplate jdbc, String execution) {
        return jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=(SELECT binding_id FROM sq_response_execution WHERE execution_id=?)", String.class, execution);
    }

    private ConfigurableApplicationContext start(Path directory) {
        return MessageAcceptanceHttpTest.start(com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("directive")), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString());
    }
}
