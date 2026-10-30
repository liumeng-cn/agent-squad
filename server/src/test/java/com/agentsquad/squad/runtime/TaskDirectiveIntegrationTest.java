package com.agentsquad.squad.runtime;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.GoalTaskCreate;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.dto.TaskDirectiveSubmit;
import com.agentsquad.squad.conversation.dto.TaskDirectiveDecision;
import com.agentsquad.squad.conversation.service.GoalTaskCreationService;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.conversation.service.TaskDirectiveService;
import com.agentsquad.squad.conversation.domain.TaskDirectiveTarget;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import com.agentsquad.squad.runtime.service.PlanService;
import com.agentsquad.squad.runtime.service.WorkerDispatchService;
import com.agentsquad.squad.runtime.service.WorkerResultService;
import com.agentsquad.squad.tools.operation.OperationService;
import com.agentsquad.squad.tools.operation.PrepareRequest;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
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

    static void verifyApply(ConfigurableApplicationContext context) throws Exception {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String user = UUID.randomUUID().toString();
        var messages = context.getBean(MessageAcceptanceService.class);
        var source = messages.accept(user, null, "source", new MessageCreateRequest("写一段正式介绍", List.of()));
        var task = context.getBean(GoalTaskCreationService.class).create(user, replySession(jdbc, source.executionId()),
                new GoalTaskCreate("goal", "介绍", "写介绍", source.messageId()));
        String targetSession = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
        ObjectMapper json = context.getBean(ObjectMapper.class);
        context.getBean(WorkerDefinitionService.class).publish(new AuthView(user, "admin", List.of("ADMIN"), "csrf"), "writer-" + user,
                new WorkerDefinitionCreateRequest("writer-" + user, "1.0.0", "写作", json.readTree("{}"), json.readTree("{}"), List.of(), "default", "default"));
        var snapshots = context.getBean(ContextSnapshotService.class);
        String snapshot = snapshots.prepare(user, targetSession, "draft", "before").snapshotId();
        var plans = context.getBean(PlanService.class);
        var planRequest = new PlanPublish("original", 0, List.of(new PlanNode("draft", "writer-" + user, "1.0.0", "介绍", snapshot, List.of(), true, 1)), "ALL_REQUIRED", List.of("完成"));
        var original = plans.publish(user, targetSession, planRequest);
        String oldBinding = jdbc.queryForObject("SELECT binding_id FROM sq_worker_job WHERE job_id=?", String.class, original.jobIds().getFirst());
        jdbc.update("UPDATE sq_command SET status='DELIVERED' WHERE binding_id=?", oldBinding);
        String guard = context.getBean(WorkerDispatchService.class).claim(user, oldBinding).guardToken();
        var supplement = messages.accept(user, source.conversationId(), "supplement", new MessageCreateRequest("改成活泼风格，突出合作", List.of()));
        String session = replySession(jdbc, supplement.executionId());
        long version = version(jdbc, task.taskId());
        var directives = context.getBean(TaskDirectiveService.class);
        var request = new TaskDirectiveSubmit("supplement", task.taskId(), version, "SUPPLEMENT", "加入活泼风格与合作重点");
        var receipt = directives.submit(user, session, request);
        assertThat(receipt.status()).isEqualTo("ACCEPTED");
        assertThat(receipt.appliedVersion()).isNull();
        assertThat(directives.submit(user, session, request)).isEqualTo(receipt);
        assertThatThrownBy(() -> directives.submit(user, session, new TaskDirectiveSubmit("supplement", task.taskId(), version, "REPLACE", "替换"))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> directives.decide(user, session, new TaskDirectiveDecision(receipt.directiveId(), version, "APPLY", null))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> directives.submit(UUID.randomUUID().toString(), session, request)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> plans.publish(user, targetSession, planRequest)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> directives.decide(user, targetSession, new TaskDirectiveDecision(receipt.directiveId(), version + 1, "APPLY", null))).isInstanceOf(RuntimeBusinessException.class);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> tx.execute(status -> {
            directives.decide(user, targetSession, new TaskDirectiveDecision(receipt.directiveId(), version, "APPLY", null));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM sq_task_directive WHERE directive_id=?", String.class, receipt.directiveId())).isEqualTo("ACCEPTED");
        var applied = directives.decide(user, targetSession, new TaskDirectiveDecision(receipt.directiveId(), version, "APPLY", null));
        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(applied.goalRevision()).isEqualTo(1);
        assertThat(directives.decide(user, targetSession, new TaskDirectiveDecision(receipt.directiveId(), version, "APPLY", null))).isEqualTo(applied);
        assertThat(jdbc.queryForObject("SELECT status FROM sq_worker_job WHERE job_id=?", String.class, original.jobIds().getFirst())).isEqualTo("SUPERSEDED");
        assertThatThrownBy(() -> context.getBean(WorkerResultService.class).propose(user, oldBinding, guard, "late-turn", json.readTree("{}"))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> snapshots.prepare(user, targetSession, "draft", "before")).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> plans.publish(user, targetSession, planRequest)).isInstanceOf(RuntimeBusinessException.class);
        String after = snapshots.prepare(user, targetSession, "draft", "after").snapshotId();
        String items = jdbc.queryForObject("SELECT items_json FROM sq_context_snapshot WHERE snapshot_id=?", String.class, after);
        assertThat(items).contains("USER_SUPPLEMENT", "改成活泼风格，突出合作").doesNotContain("加入活泼风格与合作重点");
        var next = plans.publish(user, targetSession, new PlanPublish("updated", 1, List.of(new PlanNode("draft", "writer-" + user, "1.0.0", "新介绍", after, List.of(), true, 1)), "ALL_REQUIRED", List.of("完成")));
        assertThat(next.planVersion()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT goal_revision FROM sq_plan WHERE plan_id=?", Long.class, next.planId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account WHERE user_id=?", Long.class, user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT input_json FROM sq_goal_task WHERE task_id=?", String.class, task.taskId())).contains("写一段正式介绍");
    }

    static void verifyUnknown(ConfigurableApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String user = UUID.randomUUID().toString();
        var messages = context.getBean(MessageAcceptanceService.class);
        var source = messages.accept(user, null, "source", new MessageCreateRequest("完成工作", List.of()));
        var task = context.getBean(GoalTaskCreationService.class).create(user, replySession(jdbc, source.executionId()), new GoalTaskCreate("goal", "工作", "完成工作", source.messageId()));
        String target = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
        var operations = context.getBean(OperationService.class);
        var write = new PrepareRequest(user, target, "turn", "write", "mock_ticket_create", "v1", "WRITE", Map.of("title", "test"), "stable", false, null, 0, 1, "tester");
        var operation = operations.prepare(write);
        jdbc.update("UPDATE sq_operation SET status='UNKNOWN' WHERE operation_id=?", operation.operationId());
        var message = messages.accept(user, source.conversationId(), "change", new MessageCreateRequest("改成另一项工作", List.of()));
        var service = context.getBean(TaskDirectiveService.class);
        var receipt = service.submit(user, replySession(jdbc, message.executionId()), new TaskDirectiveSubmit("change", task.taskId(), version(jdbc, task.taskId()), "REPLACE", "另一工作"));
        jdbc.update("UPDATE sq_command SET status='APPLIED' WHERE task_id=?", task.taskId());
        service.dispatch(new TaskDirectiveTarget(receipt.directiveId(), user, task.taskId()));
        assertThat(jdbc.queryForObject("SELECT command_id FROM sq_task_directive WHERE directive_id=?", String.class, receipt.directiveId())).isNull();
        assertThatThrownBy(() -> service.decide(user, target, new TaskDirectiveDecision(receipt.directiveId(), version(jdbc, task.taskId()), "APPLY", null))).isInstanceOf(RuntimeBusinessException.class);
        assertThat(operations.prepare(write).operationId()).isEqualTo(operation.operationId());
        assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, operation.operationId())).isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> operations.prepare(new PrepareRequest(user, target, "turn", "write-new", "mock_ticket_create", "v1", "WRITE", Map.of("title", "new"), "new", false, null, 0, 1, "tester"))).isInstanceOf(RuntimeBusinessException.class);
        assertThat(service.decide(user, target, new TaskDirectiveDecision(receipt.directiveId(), version(jdbc, task.taskId()), "REJECT", "原操作结果尚未确认，请先处理原工作")).status()).isEqualTo("REJECTED");
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
