package com.agentsquad.squad.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * MySQL 共享执行边界与预算并发验证。
 * <p>
 * 仅使用专用测试连接，覆盖真实可重复读隔离下的账户与用户全局预算，不访问开发库。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_SCOPE_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlScopeRuntimeOwnershipTest {

    @Test
    void governedResponsePathWorksOnMySql() {
        try (ConfigurableApplicationContext context = start(50000000)) {
            ScopeRuntimeOwnershipIntegrationTest.verifyCapabilitiesWithoutTask(context);
        }
    }

    @Test
    void concurrentReservationsReadCurrentUserBalance() throws Exception {
        try (ConfigurableApplicationContext context = start(100)) {
            ScopeRuntimeOwnershipIntegrationTest.verifyConcurrentUserBudget(context);
        }
    }

    @Test
    void sharedAccountRetainsUnknownCost() {
        try (ConfigurableApplicationContext context = start(1000)) {
            ScopeRuntimeOwnershipIntegrationTest.verifySharedAccount(context);
        }
    }

    private ConfigurableApplicationContext start(long limit) {
        return ScopeRuntimeOwnershipIntegrationTest.start(System.getenv("SQUAD_SCOPE_TEST_URL"),
                System.getenv("SQUAD_SCOPE_TEST_USER"), System.getenv("SQUAD_SCOPE_TEST_PASSWORD"), limit);
    }
}
