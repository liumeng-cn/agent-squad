package com.agentsquad.squad.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

/**
 * 独立MySQL库验证对话读取或目标修订的真实事务边界，不使用开发库。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_MESSAGE_TEST_URL", matches = ".+")
class MySqlConversationReadTest {
    @Test
    void pagesAndChecksCursor() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            ConversationReadIntegrationTest.verifyHistory(context);
        }
    }

    @Test
    void renamesAndPagesTitles() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            ConversationReadIntegrationTest.verifyTitles(context);
        }
    }

    private ConfigurableApplicationContext start() {
        return MessageAcceptanceHttpTest.start(System.getenv("SQUAD_MESSAGE_TEST_URL"), System.getenv("SQUAD_MESSAGE_TEST_USER"),
                System.getenv("SQUAD_MESSAGE_TEST_PASSWORD"), UUID.randomUUID().toString());
    }
}
