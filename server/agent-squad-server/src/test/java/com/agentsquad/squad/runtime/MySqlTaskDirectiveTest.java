package com.agentsquad.squad.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

/**
 * 独立MySQL库验证对话读取或目标修订的真实事务边界，不使用开发库。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_MESSAGE_TEST_URL", matches = ".+")
class MySqlTaskDirectiveTest {
    @Test
    void appliesOnlyAtSafeVersion() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            TaskDirectiveIntegrationTest.verifyApply(context);
        }
    }

    @Test
    void unknownWritesRemainBlocked() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            TaskDirectiveIntegrationTest.verifyUnknown(context);
        }
    }

    private ConfigurableApplicationContext start() {
        return MessageAcceptanceHttpTest.start(System.getenv("SQUAD_MESSAGE_TEST_URL"), System.getenv("SQUAD_MESSAGE_TEST_USER"),
                System.getenv("SQUAD_MESSAGE_TEST_PASSWORD"), UUID.randomUUID().toString());
    }
}
