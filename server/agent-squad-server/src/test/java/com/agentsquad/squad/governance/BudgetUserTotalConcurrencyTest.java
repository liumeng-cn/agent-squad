package com.agentsquad.squad.governance;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.config.properties.WorkerModelProfileProperties;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.model.vo.ConversationVO;
import com.agentsquad.squad.model.vo.TaskReceiptVO;
import com.agentsquad.squad.service.BudgetService;
import com.agentsquad.squad.service.GoalTaskService;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
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
        String databaseUrl = PostgresTestDatabase.url(tempDirectory.resolve("squad-user-total"));
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + PostgresTestDatabase.username(),
                "--spring.datasource.password=" + PostgresTestDatabase.password(),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--agentsquad.budget.max-microunits-per-user=100")) {
            Long userId = 2L;
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            BudgetService budget = context.getBean(BudgetService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            ConversationVO first = tasks.createConversation(userId, "跨scope预算一");
            TaskReceiptVO taskOne = tasks.submit(userId, first.conversationId(), "task-one", "任务一");
            ConversationVO second = tasks.createConversation(userId, "跨scope预算二");
            TaskReceiptVO taskTwo = tasks.submit(userId, second.conversationId(), "task-two", "任务二");

            WorkerModelProfileProperties profile = new WorkerModelProfileProperties();
            profile.setPricingVersion("user-total-v1");
            profile.setMaxInputTokens(50);
            profile.setMaxOutputTokens(10);
            profile.setInputMicrounitsPerMillionTokens(1000000);
            profile.setOutputMicrounitsPerMillionTokens(1000000);

            Long scopeOne = scopeId(jdbc, taskOne.taskId());
            Long scopeTwo = scopeId(jdbc, taskTwo.taskId());
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

    private boolean reserveModel(BudgetService budget, Long userId, Long scopeId,
                                 String usageKey, String hash, WorkerModelProfileProperties profile,
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

    private boolean reserveTool(BudgetService budget, Long userId, Long scopeId,
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

    private Long scopeId(JdbcTemplate jdbc, Long taskId) {
        return jdbc.queryForObject("SELECT scope_id FROM sq_goal_task WHERE task_id = ?", Long.class, taskId);
    }
}
