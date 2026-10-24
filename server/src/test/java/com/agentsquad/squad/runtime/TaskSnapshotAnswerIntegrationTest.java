package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.presentation.dto.TaskSnapshot;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.presentation.service.TaskSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 快照答案回填测试：answer.completed 事件写入后，快照应能读出最终答案文本。
 */
class TaskSnapshotAnswerIntegrationTest {

    @Test
    void shouldPopulateAnswerFromCompletedEvent(@TempDir Path tempDirectory) {
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
            TaskSnapshotService snapshotService = context.getBean(TaskSnapshotService.class);
            TaskEventMapper eventMapper = context.getBean(TaskEventMapper.class);

            var conversation = goalTaskService.createConversation(userId, "答案回填");
            var receipt = goalTaskService.submit(userId, conversation.conversationId(),
                    "answer-test-1", "请给出最终回答");

            TaskSnapshot before = snapshotService.snapshot(userId, receipt.taskId());
            assertThat(before.answer().text()).isEmpty();
            assertThat(before.answer().completed()).isFalse();

            String answerText = "这是最终回答";
            eventMapper.insertProjected(userId, receipt.taskId(), 2, "completion:test",
                    "answer.completed", "{\"text\":\"" + answerText + "\",\"evidenceRefs\":[]}",
                    LocalDateTime.now(ZoneOffset.UTC));

            TaskSnapshot after = snapshotService.snapshot(userId, receipt.taskId());
            assertThat(after.answer().text()).isEqualTo(answerText);
            assertThat(after.answer().completed()).isTrue();
            assertThat(after.cursor()).isEqualTo(receipt.taskId() + ":2");
        }
    }
}
