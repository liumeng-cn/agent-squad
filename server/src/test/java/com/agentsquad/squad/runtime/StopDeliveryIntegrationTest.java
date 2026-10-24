package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.integration.agentscope.CommandDeliveryService;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.conversation.repository.*;
import com.agentsquad.squad.conversation.service.ConversationCleanupScheduler;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STOP 命令投递集成：原生会话无活跃运行时，停止意图就地取消目标并释放会话占用，
 * 删除中的会话随后被清理调度器置为 DELETED。全程不调用模型。
 */
class StopDeliveryIntegrationTest {

    @Test
    void shouldFinalizeCancelledTaskWhenNoActiveNativeRun(@TempDir Path tempDirectory) {
        String databaseUrl = "jdbc:h2:file:" + tempDirectory.resolve("squad")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        String[] properties = {
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.flyway.enabled=true",
                "--agentsquad.delivery.enabled=true",
                "--agentsquad.delivery.poll-delay-ms=60000",
                "--agentsquad.model.api-key=test-key",
                "--agentsquad.model.base-url=https://example.invalid/v1",
                "--agentsquad.model.name=test-model"
        };
        String userId = "00000000-0000-0000-0000-000000000001";
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class, properties)) {
            ConversationMapper conversationMapper = context.getBean(ConversationMapper.class);
            TaskMapper taskMapper = context.getBean(TaskMapper.class);
            BindingMapper bindingMapper = context.getBean(BindingMapper.class);
            CommandMapper commandMapper = context.getBean(CommandMapper.class);
            TaskEventMapper taskEventMapper = context.getBean(TaskEventMapper.class);
            CommandDeliveryService deliveryService = context.getBean(CommandDeliveryService.class);
            GoalTaskService goalTaskService = context.getBean(GoalTaskService.class);
            ConversationCleanupScheduler cleanupScheduler = context.getBean(ConversationCleanupScheduler.class);

            String conversationId = UUID.randomUUID().toString();
            String taskId = UUID.randomUUID().toString();
            String bindingId = UUID.randomUUID().toString();
            String commandId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            conversationMapper.insert(conversationId, userId, "停止投递");
            taskMapper.insert(taskId, userId, conversationId, "{\"message\":\"停止我\"}");
            bindingMapper.insertMaster(bindingId, userId, taskId, MasterAgentIdentity.AGENT_ID,
                    UUID.randomUUID().toString(), now);
            assertThat(conversationMapper.activateTask(userId, conversationId, taskId)).isEqualTo(1);
            taskEventMapper.insertAccepted(userId, taskId, "command:" + UUID.randomUUID(),
                    "{\"taskId\":\"" + taskId + "\"}", now);
            assertThat(taskMapper.updateCancelling(userId, taskId, 0, now)).isEqualTo(1);

            String stopPayload = "{\"action\":\"STOP\"}";
            commandMapper.insertControl(commandId, userId, "task.cancel:" + taskId,
                    "stop-delivery-test", sha256(stopPayload), taskId, bindingId, "STOP", stopPayload, now);

            deliveryService.deliver(commandId);

            assertThat(commandMapper.findOwned(userId, commandId).status()).isEqualTo("DELIVERED");
            assertThat(commandMapper.findOwned(userId, commandId).nativeStatus()).isEqualTo("no-active-run");
            assertThat(goalTaskService.findTask(userId, taskId).status()).isEqualTo("CANCELLED");
            assertThat(goalTaskService.listEvents(userId, taskId, 0))
                    .extracting(event -> event.type())
                    .containsExactly("task.accepted", "task.completed");

            assertThat(conversationMapper.markDeleting(userId, conversationId, 2)).isEqualTo(1);
            cleanupScheduler.cleanupDeletingConversations();
            assertThat(goalTaskService.listConversations(userId, null, 20).items()).isEmpty();
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
