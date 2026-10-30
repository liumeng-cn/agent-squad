package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.ConversationCleanupScheduler;
import com.agentsquad.squad.conversation.service.ConversationDeletionService;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 删除会话时在途目标的就地收尾：未受理目标直接 CANCELLED，
 * 无在途目标后清理调度器把会话置为 DELETED 并隐藏。
 */
class ConversationDeletionTaskSettleTest {

    @Test
    void shouldCancelQueuedTaskAndCleanupConversation(@TempDir Path tempDirectory) {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("squad"));
        String[] properties = {
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.flyway.enabled=true"
        };
        String userId = "00000000-0000-0000-0000-000000000001";
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class, properties)) {
            GoalTaskService goalTaskService = context.getBean(GoalTaskService.class);
            ConversationDeletionService deletionService = context.getBean(ConversationDeletionService.class);
            ConversationCleanupScheduler cleanupScheduler = context.getBean(ConversationCleanupScheduler.class);

            ConversationResponse conversation = goalTaskService.createConversation(userId, "删除收尾");
            TaskReceipt receipt = goalTaskService.submit(userId, conversation.conversationId(),
                    "delete-settle-1", "未受理的目标");
            long currentVersion = goalTaskService
                    .getConversation(userId, conversation.conversationId()).version();

            deletionService.delete(userId, conversation.conversationId(), "delete-key-1", currentVersion);

            assertThat(context.getBean(TaskMapper.class).findOwned(userId, receipt.taskId()).status()).isEqualTo("CANCELLED");
            assertThatThrownBy(() -> goalTaskService.findTask(userId, receipt.taskId()))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> goalTaskService.listConversationTasks(userId, conversation.conversationId(), null, 20))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> goalTaskService.listEvents(userId, receipt.taskId(), 0))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThat(context.getBean(TaskEventMapper.class).listAfter(userId, receipt.taskId(), 0))
                    .extracting(event -> event.eventType()).containsExactly("task.accepted", "task.completed");

            cleanupScheduler.cleanupDeletingConversations();

            assertThat(goalTaskService.listConversations(userId, null, 20).items()).isEmpty();
        }
    }
}
