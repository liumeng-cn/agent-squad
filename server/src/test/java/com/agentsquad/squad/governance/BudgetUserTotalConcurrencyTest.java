package com.agentsquad.squad.governance;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
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

/**
 * 用户全局预算上限的跨 scope 并发验证（D6 3.1）。
 * <p>
 * 同一用户在不同 scope（任务）并发预留，用户行锁必须使总额不超过用户总上限；
 * BudgetReservationConcurrencyTest 已覆盖单 scope 内的任务上限，此处补齐跨 scope 的用户总账。
 */
class BudgetUserTotalConcurrencyTest {

    @Test
    void concurrentReservationsAcrossScopesCannotExceedUserTotal(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = MySqlTestDatabase.url(tempDirectory.resolve("squad-user-total"));
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + MySqlTestDatabase.username(),
                "--spring.datasource.password=" + MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.budget.max-microunits-per-user=100")) {
            String userId = "00000000-0000-0000-0000-000000000002";
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            BudgetService budget = context.getBean(BudgetService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            ConversationResponse first = tasks.createConversation(userId, "跨scope预算一");
            TaskReceipt taskOne = tasks.submit(userId, first.conversationId(), "task-one", "任务一");
            ConversationResponse second = tasks.createConversation(userId, "跨scope预算二");
            TaskReceipt taskTwo = tasks.submit(userId, second.conversationId(), "task-two", "任务二");

            WorkerModelProfile profile = new WorkerModelProfile();
            profile.setPricingVersion("user-total-v1");
            profile.setMaxInputTokens(50);
            profile.setMaxOutputTokens(10);
            profile.setInputMicrounitsPerMillionTokens(1000000);
            profile.setOutputMicrounitsPerMillionTokens(1000000);

            String scopeOne = scopeId(jdbc, taskOne.taskId());
            String scopeTwo = scopeId(jdbc, taskTwo.taskId());
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Boolean> model = executor.submit(() -> reserveModel(budget, userId, scopeOne,
                        "model:one", "a".repeat(64), profile, ready, start));
                Future<Boolean> tool = executor.submit(() -> reserveTool(budget, userId, scopeTwo,
                        ready, start));
                ready.await();
                start.countDown();

                assertThat((model.get() ? 1 : 0) + (tool.get() ? 1 : 0)).isEqualTo(1);
                Long total = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(reserved_units), 0) FROM sq_budget_reservation WHERE user_id = ?",
                        Long.class, userId);
                assertThat(total).isLessThanOrEqualTo(100L);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private boolean reserveModel(BudgetService budget, String userId, String scopeId,
                                 String usageKey, String hash, WorkerModelProfile profile,
                                 CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            budget.reserveModel(userId, scopeId, usageKey, hash, "default", profile);
            return true;
        } catch (RuntimeBusinessException exception) {
            return false;
        }
    }

    private boolean reserveTool(BudgetService budget, String userId, String scopeId,
                                CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            budget.reserve(userId, scopeId, "tool:two", "TOOL", 60, "test");
            return true;
        } catch (RuntimeBusinessException exception) {
            return false;
        }
    }

    private String scopeId(JdbcTemplate jdbc, String taskId) {
        return jdbc.queryForObject("SELECT scope_id FROM sq_goal_task WHERE task_id = ?", String.class, taskId);
    }
}
