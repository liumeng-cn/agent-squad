package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目专用 MySQL 实例联调；普通离线测试未配置地址时跳过。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_DB_URL", matches = "jdbc:mysql:.*")
class MySqlGoalTaskIntegrationTest {

    @Test
    void shouldMigrateAndCommitAtomicAcceptanceOnMySql() {
        String userId = UUID.randomUUID().toString();
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none")) {
            GoalTaskService service = context.getBean(GoalTaskService.class);
            ConversationResponse conversation = service.createConversation(userId, "MySQL 受理测试");
            TaskReceipt receipt = service.submit(userId, conversation.conversationId(),
                    UUID.randomUUID().toString(), "检查事务链路");
            assertThat(service.findTask(userId, receipt.taskId()).status()).isEqualTo("QUEUED");
            assertThat(service.findCommand(userId, receipt.commandId()).status()).isEqualTo("PENDING");
            assertThat(service.listEvents(userId, receipt.taskId(), 0)).hasSize(1);
        }
    }
}
