package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受理链路的数据库行为测试：按同一个文件型测试库重启应用，检查幂等与归属仍成立。
 *
 * <p>这是事务与 MyBatis 的验证，不代表 MySQL、AgentScope 原生投递已经验证。</p>
 */
class GoalTaskPersistenceTest {

    @Test
    void shouldKeepTaskCommandAndEventAcrossApplicationRestart(@TempDir Path tempDirectory) {
        String databaseUrl = "jdbc:h2:file:" + tempDirectory.resolve("squad")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        String[] properties = {
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.flyway.enabled=true"
        };
        String userId = "00000000-0000-0000-0000-000000000001";
        String otherUser = "00000000-0000-0000-0000-000000000002";
        TaskReceipt receipt;
        try (ConfigurableApplicationContext first = SpringApplication.run(AgentSquadApplication.class, properties)) {
            GoalTaskService service = first.getBean(GoalTaskService.class);
            ConversationResponse conversation = service.createConversation(userId, "服务排障");
            receipt = service.submit(userId, conversation.conversationId(), "request-1", "检查错误日志");
            assertThat(service.submit(userId, conversation.conversationId(), "request-1", "检查错误日志"))
                    .isEqualTo(receipt);
            assertThat(service.listEvents(userId, receipt.taskId(), 0)).hasSize(1);
            assertThatThrownBy(() -> service.submit(userId, conversation.conversationId(),
                    "request-1", "换一个目标")).isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.submit(userId, conversation.conversationId(),
                    "request-2", "新目标")).isInstanceOf(RuntimeBusinessException.class);
        }
        try (ConfigurableApplicationContext restarted = SpringApplication.run(AgentSquadApplication.class, properties)) {
            GoalTaskService service = restarted.getBean(GoalTaskService.class);
            assertThat(service.findTask(userId, receipt.taskId()).status()).isEqualTo("QUEUED");
            assertThat(service.findCommand(userId, receipt.commandId()).status()).isEqualTo("PENDING");
            assertThat(service.listEvents(userId, receipt.taskId(), 0))
                    .extracting(event -> event.type()).containsExactly("task.accepted");
            assertThatThrownBy(() -> service.findTask(otherUser, receipt.taskId()))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.findCommand(otherUser, receipt.commandId()))
                    .isInstanceOf(RuntimeBusinessException.class);
        }
    }
}
