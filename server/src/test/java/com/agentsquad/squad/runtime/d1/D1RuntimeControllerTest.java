package com.agentsquad.squad.runtime.d1;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** D1 纵向切片测试：验证用户归属和受理收据链路。 */
class D1RuntimeControllerTest {

    @Test
    void shouldCreateUserOwnedTaskWithDurableCommandReceipt() throws Exception {
        Path file = Files.createTempFile("d1-command", ".log");
        Files.deleteIfExists(file);
        D1RuntimeStore store = new D1RuntimeStore(file);
        D1Conversation conversation = store.createConversation("user-a", "排障");

        D1Task task = store.createTask("user-a", conversation.id(), "检查服务日志");

        assertThat(task.status()).isEqualTo("QUEUED");
        assertThat(task.commandId()).isNotBlank();
        assertThat(store.requireTask("user-a", task.id())).isEqualTo(task);
        assertThat(store.events("user-a", task.id()))
                .extracting(D1TaskEvent::type)
                .containsExactly("TASK_ACCEPTED");
    }
}
