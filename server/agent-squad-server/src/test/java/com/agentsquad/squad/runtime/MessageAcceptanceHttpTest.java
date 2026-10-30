package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.artifact.service.ArtifactService;
import com.agentsquad.squad.conversation.dto.MessageReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 正式消息受理HTTP集成验证。
 * <p>
 * 验证网关身份缺失、首发原子性、并发幂等、附件回滚和在途目标不锁消息；不宣称SDK回复已经执行。
 */
class MessageAcceptanceHttpTest {

    @Test
    void concurrentFirstSendCreatesOneConversationAndReceipt(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(databaseUrl(directory), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString())) {
            verifyFirstSend(context);
        }
    }

    @Test
    void invalidAttachmentRollsBackWholeFirstSend(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(databaseUrl(directory), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString())) {
            verifyAttachments(context);
        }
    }

    @Test
    void activeTaskDoesNotRejectNewMessages(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(databaseUrl(directory), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString())) {
            verifyDuringTask(context);
        }
    }

    @Test
    void retryAfterRestartReturnsOriginalFirstReceipt(@TempDir Path directory) throws Exception {
        String ownerId = UUID.randomUUID().toString();
        String url = databaseUrl(directory);
        MessageReceipt original;
        try (ConfigurableApplicationContext context = start(url, com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), ownerId)) {
            original = send(context, actor(context), "/api/v1/conversations/messages", "restart-first",
                    Map.of("text", "重启后继续交流"));
        }
        try (ConfigurableApplicationContext context = start(url, com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), ownerId)) {
            assertThat(send(context, actor(context), "/api/v1/conversations/messages", "restart-first",
                    Map.of("text", "重启后继续交流"))).isEqualTo(original);
            assertThat(count(context, "sq_conversation", ownerId)).isEqualTo(1);
            assertThat(count(context, "sq_message", ownerId)).isEqualTo(1);
        }
    }

    static ConfigurableApplicationContext start(String url, String username, String password, String ownerId) {
        return SpringApplication.run(AgentSquadApplication.class, "--server.port=0",
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password,
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=false", "--agentsquad.auth.username=tester",
                "--agentsquad.auth.password=test-only", "--agentsquad.auth.role=USER",
                "--agentsquad.auth.user-id=" + ownerId);
    }

    static void verifyFirstSend(ConfigurableApplicationContext context) throws Exception {
        MessageTestActor actor = actor(context);
        MockMvc mvc = mvc(context);
        ObjectMapper json = context.getBean(ObjectMapper.class);
        String path = "/api/v1/conversations/messages";
        mvc.perform(post(path).header("Idempotency-Key", "unauth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"你好\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Idempotency-Key", "no-identity")
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"你好\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")
                .header("Idempotency-Key", "identity-injection").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"你好\",\"taskId\":\"forged\"}")).andExpect(status().isBadRequest());
        assertThat(count(context, "sq_conversation", actor.view().userId())).isZero();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        MessageReceipt first;
        try (var threads = Executors.newFixedThreadPool(2)) {
            var a = threads.submit(() -> concurrentSend(context, actor, ready, start));
            var b = threads.submit(() -> concurrentSend(context, actor, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first = a.get(20, TimeUnit.SECONDS);
            assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(first);
        }
        assertThat(first.status().name()).isEqualTo("QUEUED");
        assertThat(first.cursor()).isEqualTo(first.conversationId() + ":0:2");
        for (String table : List.of("sq_conversation", "sq_message", "sq_response_execution", "sq_runtime_binding", "sq_command", "sq_execution_scope", "sq_budget_account")) {
            assertThat(count(context, table, actor.view().userId())).as(table).isEqualTo(1);
        }
        assertThat(count(context, "sq_conversation_event", actor.view().userId())).isEqualTo(2);
        assertThat(count(context, "sq_goal_task", actor.view().userId())).isZero();
        assertThat(count(context, "sq_budget_reservation", actor.view().userId())).isZero();
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT action FROM sq_command WHERE command_id = ?", String.class, first.commandId()))
                .isEqualTo("MESSAGE_SUBMIT");
        assertThat(jdbc.queryForObject("SELECT native_turn_id FROM sq_command WHERE command_id = ?", String.class, first.commandId())).isNull();
        mvc.perform(post(path).header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")
                .header("Idempotency-Key", "concurrent-first").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("text", "变更内容")))).andExpect(status().isConflict());
        MessageTestActor other = new MessageTestActor(new com.agentsquad.squad.identity.AuthView(UUID.randomUUID().toString(), "other", java.util.List.of("USER"), null));
        mvc.perform(post("/api/v1/conversations/{id}/messages", first.conversationId()).header("X-User-Id", other.view().userId()).header("X-User-Role", "USER")
                        .header("Idempotency-Key", "foreign")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"不能读写别人的对话\"}"))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE sq_conversation SET status = 'DELETING', generation = generation + 1 WHERE user_id = ?", actor.view().userId());
        mvc.perform(post(path).header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")
                .header("Idempotency-Key", "concurrent-first").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"你好，介绍一下能力\"}")).andExpect(status().isNotFound());
    }

    static void verifyAttachments(ConfigurableApplicationContext context) throws Exception {
        MessageTestActor actor = actor(context);
        MockMvc mvc = mvc(context);
        ObjectMapper json = context.getBean(ObjectMapper.class);
        String owner = actor.view().userId();
        mvc.perform(post("/api/v1/conversations/messages").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")
                        .header("Idempotency-Key", "attachment-first")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                Map.of("text", "分析材料", "artifactIds", List.of(UUID.randomUUID().toString())))))
                .andExpect(status().isNotFound());
        for (String table : List.of("sq_conversation", "sq_message", "sq_response_execution", "sq_runtime_binding", "sq_command", "sq_execution_scope", "sq_budget_account", "sq_conversation_event")) {
            assertThat(count(context, table, owner)).as(table).isZero();
        }
        ArtifactService artifacts = context.getBean(ArtifactService.class);
        String file = artifacts.upload(owner, "message-file", "material.txt", "事实材料".getBytes(StandardCharsets.UTF_8)).artifactId();
        String expired = artifacts.upload(owner, "expired-file", "expired.txt", "过期材料".getBytes(StandardCharsets.UTF_8)).artifactId();
        context.getBean(JdbcTemplate.class).update("UPDATE sq_artifact SET expires_at = '2000-01-01' WHERE artifact_id = ?", expired);
        mvc.perform(post("/api/v1/conversations/messages").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")
                        .header("Idempotency-Key", "expired-file").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("text", "分析", "artifactIds", List.of(expired)))))
                .andExpect(status().isNotFound());
        assertThat(count(context, "sq_conversation", owner)).isZero();
        String foreign = artifacts.upload(UUID.randomUUID().toString(), "foreign-file", "other.txt", "不能引用".getBytes(StandardCharsets.UTF_8)).artifactId();
        mvc.perform(post("/api/v1/conversations/messages").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")
                        .header("Idempotency-Key", "foreign-file").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("text", "分析", "artifactIds", List.of(foreign)))))
                .andExpect(status().isNotFound());
        MessageReceipt receipt = send(context, actor, "/api/v1/conversations/messages", "attachment-first",
                Map.of("text", "分析材料", "artifactIds", List.of(file.toUpperCase(java.util.Locale.ROOT))));
        assertThat(send(context, actor, "/api/v1/conversations/messages", "attachment-first",
                Map.of("text", "分析材料", "artifactIds", List.of(file)))).isEqualTo(receipt);
        assertThat(count(context, "sq_message_artifact", owner)).isEqualTo(1);
        assertThat(count(context, "sq_scope_artifact", owner)).isEqualTo(1);
        assertThat(count(context, "sq_artifact", owner)).isEqualTo(2);
        assertThat(count(context, "sq_goal_task", owner)).isZero();
    }

    static void verifyDuringTask(ConfigurableApplicationContext context) throws Exception {
        MessageTestActor actor = actor(context);
        String owner = actor.view().userId();
        GoalTaskService goals = context.getBean(GoalTaskService.class);
        String conversation = goals.createConversation(owner, "在途对话").conversationId();
        String task = goals.submit(owner, conversation, "background", "正在处理的目标").taskId();
        String path = "/api/v1/conversations/" + conversation + "/messages";
        MessageReceipt first = send(context, actor, path, "follow-up", Map.of("text", "补充一句约束"));
        assertThat(send(context, actor, path, "follow-up", Map.of("text", "补充一句约束"))).isEqualTo(first);
        send(context, actor, path, "progress", Map.of("text", "现在进展如何？"));
        assertThat(goals.getConversation(owner, conversation).activeTaskId()).isEqualTo(task);
        assertThat(count(context, "sq_goal_task", owner)).isEqualTo(1);
        assertThat(count(context, "sq_message", owner)).isEqualTo(2);
        assertThat(count(context, "sq_response_execution", owner)).isEqualTo(2);
        assertThat(context.getBean(JdbcTemplate.class).queryForList("SELECT message_seq FROM sq_message WHERE user_id = ? ORDER BY message_seq", Long.class, owner))
                .containsExactly(1L, 2L);
    }

    static MessageTestActor actor(ConfigurableApplicationContext context) {
        String userId = context.getEnvironment().getProperty("agentsquad.auth.user-id");
        return new MessageTestActor(new com.agentsquad.squad.identity.AuthView(userId, "tester", java.util.List.of("USER"), null));
    }

    static MessageReceipt send(ConfigurableApplicationContext context, MessageTestActor actor, String path,
                               String key, Map<String, Object> body) throws Exception {
        ObjectMapper json = context.getBean(ObjectMapper.class);
        String response = mvc(context).perform(post(path).header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readValue(response, MessageReceipt.class);
    }

    private static MessageReceipt concurrentSend(ConfigurableApplicationContext context, MessageTestActor actor,
                                                 CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("消息并发测试启动超时");
        }
        return send(context, actor, "/api/v1/conversations/messages", "concurrent-first", Map.of("text", "你好，介绍一下能力"));
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
    }

    private static long count(ConfigurableApplicationContext context, String table, String owner) {
        return context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, owner);
    }

    private String databaseUrl(Path directory) {
        return com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("messages"));
    }
}
