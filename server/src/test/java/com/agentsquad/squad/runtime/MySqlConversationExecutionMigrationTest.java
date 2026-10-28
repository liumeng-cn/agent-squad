package com.agentsquad.squad.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * MySQL 8.4 会话执行迁移实验。
 * <p>
 * 仅使用明确提供的全新隔离库，复用旧任务升级与归属验收，不读取实际开发库配置。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_MIGRATION_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlConversationExecutionMigrationTest {

    @Test
    void shouldUpgradeIsolatedMySqlWithoutLosingFacts() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv("SQUAD_MIGRATION_TEST_URL"),
                System.getenv("SQUAD_MIGRATION_TEST_USER"),
                System.getenv("SQUAD_MIGRATION_TEST_PASSWORD"));
        ConversationExecutionMigrationTest.verifyUpgrade(dataSource);
    }
}
