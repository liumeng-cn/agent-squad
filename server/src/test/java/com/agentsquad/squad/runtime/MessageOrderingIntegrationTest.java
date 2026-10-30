package com.agentsquad.squad.runtime;

import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.CommandClaimService;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.conversation.service.ResponseContextService;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import com.agentsquad.squad.runtime.service.PlanService;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.conversation.service.ResponseControlService;
import com.agentsquad.squad.interaction.repository.InteractionMapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话消息顺序与进度快照验证。
 * <p>
 * 通过正式受理和领取服务检查倒序阻断、等待让行、跨会话隔离及固定目标事实；不调用模型。
 */
class MessageOrderingIntegrationTest {
    @Test
    void laterMessageCannotOvertakeEarlierReply(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            verifyOrdering(context);
        }
    }

    @Test
    void progressFactsAreOwnedAndFrozenWithSource(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            verifyTaskFacts(context);
        }
    }

    static void verifyOrdering(ConfigurableApplicationContext context) {
        String user = UUID.randomUUID().toString();
        var messages = context.getBean(MessageAcceptanceService.class);
        var first = messages.accept(user, null, "first", new MessageCreateRequest("第一条消息", List.of()));
        var second = messages.accept(user, first.conversationId(), "second", new MessageCreateRequest("第二条消息", List.of()));
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("UPDATE sq_command SET due_at='2000-01-01' WHERE user_id=?", user);
        var claims = context.getBean(CommandClaimService.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1);
        assertThat(context.getBean(DeliveryMapper.class).findDueIds(now)).contains(first.commandId()).doesNotContain(second.commandId());
        assertThat(claims.claim(second.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60))).isFalse();
        assertThat(claims.claim(first.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60))).isTrue();
        assertThat(claims.claim(first.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60))).isFalse();
        jdbc.update("UPDATE sq_response_execution SET status='RUNNING' WHERE execution_id=?", first.executionId());
        assertThat(claims.claim(second.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60))).isFalse();
        String interaction = UUID.randomUUID().toString();
        String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, first.executionId());
        context.getBean(InteractionMapper.class).insert(interaction, user, null, null, binding, null, "FRAMEWORK", "pending-request",
                "INPUT", "请提供材料", "{\"type\":\"string\"}", 0, "a".repeat(64), 0, now.plusHours(1), user, "tester");
        jdbc.update("UPDATE sq_response_execution SET status='WAITING_INPUT' WHERE execution_id=?", first.executionId());
        assertThat(claims.claim(second.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60))).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, first.executionId())).isEqualTo("WAITING_INPUT");
        assertThat(jdbc.queryForObject("SELECT status FROM sq_interaction WHERE interaction_id=?", String.class, interaction)).isEqualTo("OPEN");
        var other = messages.accept(user, null, "other", new MessageCreateRequest("另一会话", List.of()));
        jdbc.update("UPDATE sq_command SET due_at='2000-01-01' WHERE command_id=?", other.commandId());
        assertThat(claims.claim(other.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60))).isTrue();
        jdbc.update("UPDATE sq_conversation SET generation=generation+1 WHERE conversation_id=?", first.conversationId());
        var nextGeneration = messages.accept(user, first.conversationId(), "next-generation", new MessageCreateRequest("新授权代际", List.of()));
        jdbc.update("UPDATE sq_command SET due_at='2000-01-01' WHERE command_id=?", nextGeneration.commandId());
        assertThat(claims.claim(nextGeneration.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60))).isTrue();
    }

    @Test
    void cancellationAndClaimUseSameLockOrder(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(directory)) {
            verifyConcurrentCancel(context);
        }
    }

    static void verifyConcurrentCancel(ConfigurableApplicationContext context) throws Exception {
        String user = UUID.randomUUID().toString();
        var message = context.getBean(MessageAcceptanceService.class).accept(user, null, "cancel-race", new MessageCreateRequest("消息", List.of()));
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("UPDATE sq_command SET due_at='2000-01-01' WHERE command_id=?", message.commandId());
        CountDownLatch start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var claim = threads.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1);
                return context.getBean(CommandClaimService.class).claim(message.commandId(), UUID.randomUUID().toString(), now, now.plusSeconds(60));
            });
            var cancel = threads.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return context.getBean(ResponseControlService.class).cancel(user, message.executionId(), "cancel", 0);
            });
            start.countDown();
            claim.get(20, TimeUnit.SECONDS);
            assertThat(cancel.get(20, TimeUnit.SECONDS).commandId()).isNotBlank();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM sq_command WHERE command_id=?", String.class, message.commandId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, message.executionId())).isEqualTo("CANCELLING");
    }

    static void verifyTaskFacts(ConfigurableApplicationContext context) {
        String user = UUID.randomUUID().toString();
        GoalTaskService goals = context.getBean(GoalTaskService.class);
        String conversation = goals.createConversation(user, "在途工作").conversationId();
        String task = goals.submit(user, conversation, "goal", "请完成写作").taskId();
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("UPDATE sq_goal_task SET status='RUNNING', version=version+1 WHERE task_id=?", task);
        var messages = context.getBean(MessageAcceptanceService.class);
        var query = messages.accept(user, conversation, "progress", new MessageCreateRequest("目前进展如何", List.of()));
        String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, query.executionId());
        var contexts = context.getBean(ResponseContextService.class);
        var snapshot = contexts.freeze(user, binding);
        String replySession = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
        assertThatThrownBy(() -> context.getBean(ContextSnapshotService.class).prepare(user, replySession, "draft", "spoof"))
                .isInstanceOfSatisfying(RuntimeBusinessException.class, exception -> assertThat(exception.code()).isEqualTo(RuntimeExceptionCode.NOT_FOUND));
        PlanPublish attempted = new PlanPublish("spoof-plan", 0, List.of(new PlanNode("draft", "writer", "1", "目标",
                UUID.randomUUID().toString(), List.of(), true, 1)), "ALL_REQUIRED", List.of("完成"));
        assertThatThrownBy(() -> context.getBean(PlanService.class).publish(user, replySession, attempted))
                .isInstanceOfSatisfying(RuntimeBusinessException.class, exception -> assertThat(exception.code()).isEqualTo(RuntimeExceptionCode.NOT_FOUND));
        assertThat(snapshot.activeTask().taskId()).isEqualTo(task);
        assertThat(snapshot.activeTask().status()).isEqualTo("RUNNING");
        jdbc.update("UPDATE sq_goal_task SET status='WAITING_INPUT', version=version+1 WHERE task_id=?", task);
        assertThat(contexts.freeze(user, binding)).isEqualTo(snapshot);
        jdbc.update("UPDATE sq_conversation SET generation=generation+1 WHERE conversation_id=?", conversation);
        var newMessage = messages.accept(user, conversation, "new-context", new MessageCreateRequest("新代际进展", List.of()));
        String newBinding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, newMessage.executionId());
        assertThat(contexts.freeze(user, newBinding).activeTask()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task WHERE user_id=?", Long.class, user)).isEqualTo(1);
    }

    private ConfigurableApplicationContext start(Path directory) {
        return MessageAcceptanceHttpTest.start(com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("order")), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString());
    }
}
