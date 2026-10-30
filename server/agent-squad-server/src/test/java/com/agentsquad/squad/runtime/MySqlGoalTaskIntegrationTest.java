package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.artifact.service.ArtifactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;
import java.util.List;
import java.nio.charset.StandardCharsets;

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
            ArtifactService artifacts = context.getBean(ArtifactService.class);
            byte[] content = "MySQL 产物完整性验证".getBytes(StandardCharsets.UTF_8);
            String artifactId = artifacts.upload(userId, UUID.randomUUID().toString(), "evidence.txt", content)
                    .artifactId();
            ConversationResponse conversation = service.createConversation(userId, "MySQL 受理测试");
            TaskReceipt receipt = service.submit(userId, conversation.conversationId(),
                    UUID.randomUUID().toString(), "检查事务链路", List.of(artifactId));
            assertThat(service.findTask(userId, receipt.taskId()).status()).isEqualTo("QUEUED");
            assertThat(service.findCommand(userId, receipt.commandId()).status()).isEqualTo("PENDING");
            assertThat(service.listEvents(userId, receipt.taskId(), 0)).hasSize(1);
            assertThat(artifacts.content(userId, artifactId).content()).isEqualTo(content);
            assertThat(artifacts.listTask(userId, receipt.taskId())).extracting("artifactId").containsExactly(artifactId);
        }
    }
}
