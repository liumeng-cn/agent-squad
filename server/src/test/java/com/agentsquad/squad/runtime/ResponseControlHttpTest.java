package com.agentsquad.squad.runtime;

import com.agentsquad.squad.conversation.service.ConversationCleanupScheduler;
import com.agentsquad.squad.conversation.service.ConversationDeletionService;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.conversation.service.ResponseExecutionService;
import com.agentsquad.squad.identity.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回答停止与会话删除的HTTP归属验证。
 * <p>
 * 校验CSRF、版本、幂等及跨用户边界；停止回答不能改写在途目标，删除须等回复安全收尾。
 */
class ResponseControlHttpTest {
    @Test
    void cancelIsOwnedVersionedIdempotentAndDoesNotCancelTask(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(directory)) {
            MessageTestActor actor = MessageAcceptanceHttpTest.actor(context);
            String user = actor.view().userId();
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            String conversation = tasks.createConversation(user, "后台目标").conversationId();
            String task = tasks.submit(user, conversation, "background", "写作目标").taskId();
            var receipt = MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/" + conversation + "/messages",
                    "question", Map.of("text", "进展如何"));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            String path = "/api/v1/executions/" + receipt.executionId() + "/cancel";
            mvc.perform(post(path).cookie(actor.cookie()).header("Idempotency-Key", "stop")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isUnauthorized());
            mvc.perform(post(path).cookie(actor.cookie()).header("X-CSRF-Token", actor.view().csrfToken()).header("Idempotency-Key", "stale")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":9}")).andExpect(status().isConflict());
            String first = mvc.perform(post(path).cookie(actor.cookie()).header("X-CSRF-Token", actor.view().csrfToken()).header("Idempotency-Key", "stop")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            String again = mvc.perform(post(path).cookie(actor.cookie()).header("X-CSRF-Token", actor.view().csrfToken()).header("Idempotency-Key", "stop")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            assertThat(again).isEqualTo(first);
            assertThat(tasks.getConversation(user, conversation).activeTaskId()).isEqualTo(task);
            assertThat(tasks.findTask(user, task).status()).isEqualTo("QUEUED");
            ReflectionTestUtils.setField(context.getBean(AuthService.class), "configuredUserId", UUID.randomUUID().toString());
            MessageTestActor other = MessageAcceptanceHttpTest.actor(context);
            mvc.perform(post(path).cookie(other.cookie()).header("X-CSRF-Token", other.view().csrfToken()).header("Idempotency-Key", "foreign")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isNotFound());
        }
    }

    @Test
    void deletionWaitsForReplyAndDoesNotPublishLateContent(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(directory)) {
            MessageTestActor actor = MessageAcceptanceHttpTest.actor(context);
            String user = actor.view().userId();
            var receipt = MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/messages", "delete", Map.of("text", "要删除的消息"));
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            context.getBean(ConversationDeletionService.class).delete(user, receipt.conversationId(), "delete",
                    tasks.getConversation(user, receipt.conversationId()).version());
            ConversationCleanupScheduler cleanup = context.getBean(ConversationCleanupScheduler.class);
            cleanup.cleanupDeletingConversations();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_conversation WHERE conversation_id=?", String.class, receipt.conversationId())).isEqualTo("DELETING");
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            context.getBean(ResponseExecutionService.class).stopped(user, binding);
            cleanup.cleanupDeletingConversations();
            assertThat(jdbc.queryForObject("SELECT status FROM sq_conversation WHERE conversation_id=?", String.class, receipt.conversationId())).isEqualTo("DELETED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event", Long.class)).isEqualTo(2);
        }
    }

    private ConfigurableApplicationContext start(Path directory) {
        return MessageAcceptanceHttpTest.start("jdbc:h2:file:" + directory.resolve("controls") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "", UUID.randomUUID().toString());
    }
}
