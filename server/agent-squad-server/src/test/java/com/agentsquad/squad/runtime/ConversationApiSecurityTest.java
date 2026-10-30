package com.agentsquad.squad.runtime;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对话新增接口的HTTP安全与代理联调回归。
 * 验证跨用户、游标、标题版本和写入身份，包含开发代理携带Origin的PATCH请求。
 */
class ConversationApiSecurityTest {
    @Test
    void readRenameAndRetryRequireCorrectIdentityAndVersion(@TempDir Path directory) throws Exception {
        try (var context = MessageAcceptanceHttpTest.start(com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("api")), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString())) {
            var actor = MessageAcceptanceHttpTest.actor(context);
            var receipt = MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/messages", "first", Map.of("text", "接口验证"));
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            String base = "/api/v1/conversations/" + receipt.conversationId();
            mvc.perform(get(base + "/snapshot")).andExpect(status().isUnauthorized());
            mvc.perform(get(base + "/snapshot").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER")).andExpect(status().isOk());
            mvc.perform(get(base + "/events").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER").param("after", "invalid")).andExpect(status().isBadRequest());
            mvc.perform(get(base + "/events").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER").param("after", receipt.conversationId() + ":1:999999")).andExpect(status().isBadRequest());
            String body = "{\"title\":\"新的标题\",\"expectedTitleVersion\":0}";
            mvc.perform(patch(base + "/title").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
            mvc.perform(patch(base + "/title").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER").header("Origin", "http://127.0.0.1:5174").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
            mvc.perform(patch(base + "/title").header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
            String retry = "/api/v1/executions/" + receipt.executionId() + "/retry";
            mvc.perform(post(retry).header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isUnauthorized());
            mvc.perform(post(retry).header("X-User-Id", actor.view().userId()).header("X-User-Role", "USER").header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isConflict());
            var other = new MessageTestActor(new com.agentsquad.squad.identity.AuthView(UUID.randomUUID().toString(), "other", java.util.List.of("USER"), null));
            mvc.perform(get(base + "/snapshot").header("X-User-Id", other.view().userId()).header("X-User-Role", "USER")).andExpect(status().isNotFound());
            mvc.perform(get(base + "/events").header("X-User-Id", other.view().userId()).header("X-User-Role", "USER").param("after", receipt.conversationId() + ":1:0")).andExpect(status().isNotFound());
            mvc.perform(post(retry).header("X-User-Id", other.view().userId()).header("X-User-Role", "USER").header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isNotFound());
        }
    }
}
