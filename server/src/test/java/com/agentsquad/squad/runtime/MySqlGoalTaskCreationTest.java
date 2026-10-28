package com.agentsquad.squad.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

/**
 * MySQL目标创建事务验证。
 * <p>
 * 使用独立测试库覆盖可重复读下工具重放与预算继承，不访问开发数据库或外部模型。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_MESSAGE_TEST_URL", matches = ".+")
class MySqlGoalTaskCreationTest {
    @Test
    void ownershipAndBudgetStayConsistent() {
        try (ConfigurableApplicationContext context = start()) {
            GoalTaskCreationIntegrationTest.verifyOwnershipAndBudget(context);
        }
    }

    @Test
    void concurrentToolReplayIsAtomic() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            GoalTaskCreationIntegrationTest.verifyConcurrentCreation(context);
        }
    }

    private ConfigurableApplicationContext start() {
        return MessageAcceptanceHttpTest.start(System.getenv("SQUAD_MESSAGE_TEST_URL"), System.getenv("SQUAD_MESSAGE_TEST_USER"),
                System.getenv("SQUAD_MESSAGE_TEST_PASSWORD"), UUID.randomUUID().toString());
    }
}
