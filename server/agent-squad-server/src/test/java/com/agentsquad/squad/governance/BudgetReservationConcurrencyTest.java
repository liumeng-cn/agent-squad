package com.agentsquad.squad.governance;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.governance.service.BudgetService;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 父目标预算的并发预留验证。
 *
 * 两个模型请求同时竞争同一余额，数据库目标行锁必须只允许一笔预留通过。
 */
class BudgetReservationConcurrencyTest {

    @Test
    void concurrentReservationsCannotExceedTaskLimit(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("squad"));
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.budget.max-microunits-per-task=100")) {
            String userId = "00000000-0000-0000-0000-000000000001";
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            BudgetService budget = context.getBean(BudgetService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ConversationResponse conversation = tasks.createConversation(userId, "并发预算");
            TaskReceipt task = tasks.submit(userId, conversation.conversationId(), "budget-task", "测试预算");

            WorkerModelProfile profile = new WorkerModelProfile();
            profile.setPricingVersion("concurrent-v1");
            profile.setMaxInputTokens(50);
            profile.setMaxOutputTokens(10);
            profile.setInputMicrounitsPerMillionTokens(1000000);
            profile.setOutputMicrounitsPerMillionTokens(1000000);
            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch ready = new CountDownLatch(3);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Boolean> first = executor.submit(() -> reserveModel(budget, userId,
                        scopeId(jdbc, task.taskId()), "model:master", "default", "a".repeat(64),
                        profile, ready, start));
                Future<Boolean> second = executor.submit(() -> reserveModel(budget, userId,
                        scopeId(jdbc, task.taskId()), "model:worker", "review", "b".repeat(64),
                        profile, ready, start));
                Future<Boolean> third = executor.submit(() -> reserveTool(budget, userId,
                        scopeId(jdbc, task.taskId()), ready, start));
                ready.await();
                start.countDown();

                assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)
                        + (third.get() ? 1 : 0)).isEqualTo(1);
                Long units = jdbc.queryForObject(
                        "SELECT SUM(reserved_units) FROM sq_budget_reservation WHERE task_id = ?",
                        Long.class, task.taskId());
                assertThat(units).isEqualTo(60L);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void modelReservationSettlesTrustedUsageAndRetainsUnknownCharge(@TempDir Path tempDirectory) {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("squad"));
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.budget.max-microunits-per-task=100")) {
            String userId = "00000000-0000-0000-0000-000000000001";
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            BudgetService budget = context.getBean(BudgetService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            WorkerModelProfile profile = new WorkerModelProfile();
            profile.setPricingVersion("fixture-v1");
            profile.setInputMicrounitsPerMillionTokens(1000000);
            profile.setOutputMicrounitsPerMillionTokens(1000000);
            profile.setMaxInputTokens(50);
            profile.setMaxOutputTokens(20);

            ConversationResponse firstConversation = tasks.createConversation(userId, "模型预算");
            TaskReceipt firstTask = tasks.submit(userId, firstConversation.conversationId(),
                    "model-task", "预算结算");
            String hash = "a".repeat(64);
            budget.reserveModel(userId, scopeId(jdbc, firstTask.taskId()), "model:first", hash, "default", profile);
            assertThatThrownBy(() -> budget.reserveModel(userId, scopeId(jdbc, firstTask.taskId()),
                    "model:first", hash, "default", profile))
                    .isInstanceOf(RuntimeBusinessException.class);
            budget.settleModel(userId, scopeId(jdbc, firstTask.taskId()), "model:first", profile, 10, 5);
            budget.reserve(userId, scopeId(jdbc, firstTask.taskId()), "tool:after-model", "TOOL", 85, "test");
            Long consumed = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN status = 'SETTLED' "
                    + "THEN settled_units ELSE reserved_units END), 0) "
                    + "FROM sq_budget_reservation WHERE task_id = ?", Long.class, firstTask.taskId());
            assertThat(consumed).isEqualTo(100L);

            ConversationResponse secondConversation = tasks.createConversation(userId, "未知计费");
            TaskReceipt secondTask = tasks.submit(userId, secondConversation.conversationId(),
                    "unknown-task", "未知计费");
            budget.reserveModel(userId, scopeId(jdbc, secondTask.taskId()), "model:unknown", hash, "default", profile);
            budget.markModelUnknown(userId, scopeId(jdbc, secondTask.taskId()), "model:unknown");
            assertThatThrownBy(() -> budget.reserve(userId, scopeId(jdbc, secondTask.taskId()),
                    "tool:over-limit", "TOOL", 40, "test"))
                    .isInstanceOf(RuntimeBusinessException.class);
        }
    }

    private boolean reserveModel(BudgetService budget, String userId, String taskId,
                                 String usageKey, String profileKey, String hash,
                                 WorkerModelProfile profile, CountDownLatch ready,
                                 CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            budget.reserveModel(userId, taskId, usageKey, hash, profileKey, profile);
            return true;
        } catch (RuntimeBusinessException exception) {
            return false;
        }
    }

    private boolean reserveTool(BudgetService budget, String userId, String taskId,
                                CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            budget.reserve(userId, taskId, "tool:concurrent", "TOOL", 60, "test");
            return true;
        } catch (RuntimeBusinessException exception) {
            return false;
        }
    }
    private String scopeId(JdbcTemplate jdbc, String taskId) {
        return jdbc.queryForObject("SELECT scope_id FROM sq_goal_task WHERE task_id = ?", String.class, taskId);
    }
}
