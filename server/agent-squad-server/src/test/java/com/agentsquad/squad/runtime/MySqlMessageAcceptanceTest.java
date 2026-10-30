package com.agentsquad.squad.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

/**
 * MySQL消息受理HTTP验收。
 * <p>
 * 专用隔离库验证首发并发、附件整体回滚和在途目标期间的消息受理，不接入真实模型。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_MESSAGE_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlMessageAcceptanceTest {

    @Test
    void concurrentFirstSendIsAtomic() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            MessageAcceptanceHttpTest.verifyFirstSend(context);
        }
    }

    @Test
    void attachmentFailureLeavesNoPartialConversation() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            MessageAcceptanceHttpTest.verifyAttachments(context);
        }
    }

    @Test
    void backgroundTaskDoesNotLockMessages() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            MessageAcceptanceHttpTest.verifyDuringTask(context);
        }
    }

    private ConfigurableApplicationContext start() {
        return MessageAcceptanceHttpTest.start(System.getenv("SQUAD_MESSAGE_TEST_URL"),
                System.getenv("SQUAD_MESSAGE_TEST_USER"), System.getenv("SQUAD_MESSAGE_TEST_PASSWORD"),
                UUID.randomUUID().toString());
    }
}
