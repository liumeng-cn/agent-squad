package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.runtime.dto.ConversationResponse;
import com.agentsquad.squad.runtime.dto.TaskReceipt;
import com.agentsquad.squad.runtime.service.ConversationCleanupScheduler;
import com.agentsquad.squad.runtime.service.ConversationDeletionService;
import com.agentsquad.squad.runtime.service.GoalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 删除会话时在途目标的就地收尾：未受理目标直接 CANCELLED，
 * 无在途目标后清理调度器把会话置为 DELETED 并隐藏。
 */
class ConversationDeletionTaskSettleTest {

    @Test
    void shouldCancelQueuedTaskAndCleanupConversation(@TempDir Path tempDirectory) {
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

            assertThat(goalTaskService.findTask(userId, receipt.taskId()).status()).isEqualTo("CANCELLED");
            assertThat(goalTaskService.listConversationTasks(userId, conversation.conversationId(), null, 20)
                    .items()).extracting(task -> task.taskId()).containsExactly(receipt.taskId());
            assertThat(goalTaskService.listEvents(userId, receipt.taskId(), 0))
                    .extracting(event -> event.type())
                    .containsExactly("task.accepted", "task.completed");

            cleanupScheduler.cleanupDeletingConversations();

            assertThat(goalTaskService.listConversations(userId, null, 20).items()).isEmpty();
        }
    }
}
