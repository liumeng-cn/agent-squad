package com.agentsquad.squad.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

/**
 * MySQL消息有序领取与目标事实验证。
 * <p>
 * 使用独立测试库验证锁后当前读及消息代际隔离，不操作实际开发数据。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_MESSAGE_TEST_URL", matches = ".+")
class MySqlMessageOrderingTest {
    @Test
    void laterMessageCannotOvertakeEarlierReply() {
        try (ConfigurableApplicationContext context = start()) {
            MessageOrderingIntegrationTest.verifyOrdering(context);
        }
    }

    @Test
    void freezesCurrentTaskFacts() {
        try (ConfigurableApplicationContext context = start()) {
            MessageOrderingIntegrationTest.verifyTaskFacts(context);
        }
    }

    @Test
    void cancellationAndClaimDoNotInvertLocks() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            MessageOrderingIntegrationTest.verifyConcurrentCancel(context);
        }
    }

    private ConfigurableApplicationContext start() {
        return MessageAcceptanceHttpTest.start(System.getenv("SQUAD_MESSAGE_TEST_URL"), System.getenv("SQUAD_MESSAGE_TEST_USER"),
                System.getenv("SQUAD_MESSAGE_TEST_PASSWORD"), UUID.randomUUID().toString());
    }
}
