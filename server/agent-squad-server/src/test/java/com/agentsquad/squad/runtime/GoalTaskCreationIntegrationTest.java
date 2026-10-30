package com.agentsquad.squad.runtime;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.artifact.service.ArtifactService;

import java.nio.charset.StandardCharsets;

import com.agentsquad.squad.conversation.dto.GoalTaskCreate;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskCreationService;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.governance.service.BudgetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受权回复创建目标的事务与预算验证。
 * <p>
 * 从正式消息服务受理来源，验证幂等、单活目标、来源防伪、整批回滚和真实预算服务共享额度。
 */
class GoalTaskCreationIntegrationTest {
    @Test
    void inheritsBudgetAndRejectsForgedOrigins(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            verifyOwnershipAndBudget(context);
        }
    }

    @Test
    void concurrentToolReplayCreatesOneTask(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(directory)) {
            verifyConcurrentCreation(context);
        }
    }

    @Test
    void outerTransactionRollsBackEveryTaskFact(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            String user = UUID.randomUUID().toString();
            var message = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateRequest("用户原文", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String session = session(jdbc, message.executionId());
            var request = new GoalTaskCreate("goal", "写作目标", "模型整理的目标", message.messageId());
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            assertThatThrownBy(() -> transaction.execute(status -> {
                context.getBean(GoalTaskCreationService.class).create(user, session, request);
                throw new IllegalStateException("测试事务回滚");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isZero();
            for (String table : List.of("sq_execution_scope", "sq_budget_account", "sq_runtime_binding", "sq_command")) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).as(table).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT active_task_id FROM sq_conversation", String.class)).isNull();
            assertThat(context.getBean(GoalTaskCreationService.class).create(user, session, request).taskId()).isNotBlank();
        }
    }

    @Test
    void inheritedAttachmentsAreValidatedBeforeTaskCommit(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            String user = UUID.randomUUID().toString();
            String file = context.getBean(ArtifactService.class).upload(user, "file", "facts.txt",
                    "授权材料".getBytes(StandardCharsets.UTF_8)).artifactId();
            var message = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateRequest("请使用附件", List.of(file)));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var request = new GoalTaskCreate("goal", "标题", "工作目标", message.messageId());
            String session = session(jdbc, message.executionId());
            jdbc.update("UPDATE sq_artifact SET expires_at='2000-01-01' WHERE artifact_id=?", file);
            assertThatThrownBy(() -> context.getBean(GoalTaskCreationService.class).create(user, session, request))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_execution_scope", Long.class)).isEqualTo(1);
            jdbc.update("UPDATE sq_artifact SET expires_at='2030-01-01' WHERE artifact_id=?", file);
            context.getBean(GoalTaskCreationService.class).create(user, session, request);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_task_artifact", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_scope_artifact", Long.class)).isEqualTo(2);
        }
    }

    static void verifyOwnershipAndBudget(ConfigurableApplicationContext context) {
        String user = UUID.randomUUID().toString();
        var message = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateRequest("用户授权原文", List.of()));
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String session = session(jdbc, message.executionId());
        String sourceScope = jdbc.queryForObject("SELECT scope_id FROM sq_response_execution WHERE execution_id=?", String.class, message.executionId());
        jdbc.update("UPDATE sq_budget_account SET limit_microunits=100 WHERE user_id=?", user);
        BudgetService budget = context.getBean(BudgetService.class);
        budget.reserve(user, sourceScope, "source-cost", "TOOL", 60, "tester");
        jdbc.update("UPDATE sq_budget_reservation SET status='UNKNOWN' WHERE user_id=?", user);
        var goals = context.getBean(GoalTaskCreationService.class);
        var request = new GoalTaskCreate("goal", "标题", "模型整理的工作目标", message.messageId());
        assertThatThrownBy(() -> goals.create(user, session, new GoalTaskCreate("forged", "标题", "目标", UUID.randomUUID().toString())))
                .isInstanceOf(RuntimeBusinessException.class).hasMessageContaining("当前受权消息");
        assertThatThrownBy(() -> goals.create(UUID.randomUUID().toString(), session, request)).isInstanceOf(RuntimeBusinessException.class);
        TaskReceipt task = goals.create(user, session, request);
        assertThat(goals.create(user, session, request)).isEqualTo(task);
        assertThatThrownBy(() -> goals.create(user, session, new GoalTaskCreate("goal", "标题", "不同目标", message.messageId())))
                .isInstanceOf(RuntimeBusinessException.class).hasMessageContaining("内容不一致");
        assertThatThrownBy(() -> goals.create(user, session, new GoalTaskCreate("another", "标题", "另一目标", message.messageId())))
                .isInstanceOf(RuntimeBusinessException.class).hasMessageContaining("未完成目标");
        String taskSession = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
        assertThat(taskSession).isNotEqualTo(session);
        assertThatThrownBy(() -> goals.create(user, taskSession, request)).isInstanceOf(RuntimeBusinessException.class);
        String taskScope = jdbc.queryForObject("SELECT scope_id FROM sq_goal_task WHERE task_id=?", String.class, task.taskId());
        assertThat(jdbc.queryForList("SELECT DISTINCT account_id FROM sq_execution_scope WHERE user_id=?", String.class, user)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account WHERE user_id=?", Long.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT origin_message_id FROM sq_goal_task WHERE task_id=?", String.class, task.taskId())).isEqualTo(message.messageId());
        String input = jdbc.queryForObject("SELECT input_json FROM sq_goal_task WHERE task_id=?", String.class, task.taskId());
        assertThat(input).contains("用户授权原文", "模型整理的工作目标");
        jdbc.update("UPDATE sq_response_execution SET status='SUCCEEDED' WHERE execution_id=?", message.executionId());
        assertThat(goals.create(user, session, request)).isEqualTo(task);
        assertThatThrownBy(() -> budget.reserve(user, taskScope, "too-much", "TOOL", 41, "tester"))
                .isInstanceOf(RuntimeBusinessException.class);
        budget.reserve(user, taskScope, "remaining", "TOOL", 40, "tester");
        assertThat(jdbc.queryForObject("SELECT SUM(reserved_units) FROM sq_budget_reservation WHERE user_id=?", Long.class, user)).isEqualTo(100);
        jdbc.update("UPDATE sq_goal_task SET status='SUCCEEDED' WHERE task_id=?", task.taskId());
        jdbc.update("UPDATE sq_conversation SET active_task_id=NULL WHERE conversation_id=?", message.conversationId());
        assertThat(goals.create(user, session, request)).isEqualTo(task);
        assertThat(jdbc.queryForObject("SELECT active_task_id FROM sq_conversation WHERE conversation_id=?", String.class, message.conversationId())).isNull();
        jdbc.update("UPDATE sq_conversation SET generation=generation+1 WHERE conversation_id=?", message.conversationId());
        assertThatThrownBy(() -> goals.create(user, session, request)).isInstanceOf(RuntimeBusinessException.class);
    }

    static void verifyConcurrentCreation(ConfigurableApplicationContext context) throws Exception {
        String user = UUID.randomUUID().toString();
        var message = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateRequest("并发重放", List.of()));
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String session = session(jdbc, message.executionId());
        var request = new GoalTaskCreate("goal", "标题", "工作目标", message.messageId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var a = threads.submit(() -> concurrent(context, user, session, request, ready, go));
            var b = threads.submit(() -> concurrent(context, user, session, request, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task WHERE user_id=?", Long.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE user_id=? AND action='SUBMIT'", Long.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event WHERE user_id=? AND event_type='task.created'", Long.class, user)).isEqualTo(1);
    }

    private static TaskReceipt concurrent(ConfigurableApplicationContext context, String user, String session, GoalTaskCreate request,
                                          CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发测试超时");
        }
        return context.getBean(GoalTaskCreationService.class).create(user, session, request);
    }

    private static String session(JdbcTemplate jdbc, String execution) {
        return jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=(SELECT binding_id FROM sq_response_execution WHERE execution_id=?)", String.class, execution);
    }

    private ConfigurableApplicationContext start(Path directory) {
        return MessageAcceptanceHttpTest.start(com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("goals")), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString());
    }
}
