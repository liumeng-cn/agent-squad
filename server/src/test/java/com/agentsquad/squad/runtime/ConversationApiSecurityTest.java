package com.agentsquad.squad.runtime;

import com.agentsquad.squad.identity.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
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
 * 验证跨用户、游标、标题版本和写入CSRF，包含开发代理携带Origin的PATCH请求。
 */
class ConversationApiSecurityTest {
    @Test
    void readRenameAndRetryRequireCorrectIdentityAndVersion(@TempDir Path directory) throws Exception {
        try (var context = MessageAcceptanceHttpTest.start("jdbc:h2:file:" + directory.resolve("api") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "", UUID.randomUUID().toString())) {
            var actor = MessageAcceptanceHttpTest.actor(context);
            var receipt = MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/messages", "first", Map.of("text", "接口验证"));
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            String base = "/api/v1/conversations/" + receipt.conversationId();
            mvc.perform(get(base + "/snapshot")).andExpect(status().isUnauthorized());
            mvc.perform(get(base + "/snapshot").cookie(actor.cookie())).andExpect(status().isOk());
            mvc.perform(get(base + "/events").cookie(actor.cookie()).param("after", "invalid")).andExpect(status().isBadRequest());
            mvc.perform(get(base + "/events").cookie(actor.cookie()).param("after", receipt.conversationId() + ":1:999999")).andExpect(status().isBadRequest());
            String body = "{\"title\":\"新的标题\",\"expectedTitleVersion\":0}";
            mvc.perform(patch(base + "/title").cookie(actor.cookie()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
            mvc.perform(patch(base + "/title").cookie(actor.cookie()).header("Origin", "http://127.0.0.1:5174").header("X-CSRF-Token", actor.view().csrfToken()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
            mvc.perform(patch(base + "/title").cookie(actor.cookie()).header("X-CSRF-Token", actor.view().csrfToken()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
            String retry = "/api/v1/executions/" + receipt.executionId() + "/retry";
            mvc.perform(post(retry).cookie(actor.cookie()).header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isUnauthorized());
            mvc.perform(post(retry).cookie(actor.cookie()).header("X-CSRF-Token", actor.view().csrfToken()).header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isConflict());
            ReflectionTestUtils.setField(context.getBean(AuthService.class), "configuredUserId", UUID.randomUUID().toString());
            var other = MessageAcceptanceHttpTest.actor(context);
            mvc.perform(get(base + "/snapshot").cookie(other.cookie())).andExpect(status().isNotFound());
            mvc.perform(get(base + "/events").cookie(other.cookie()).param("after", receipt.conversationId() + ":1:0")).andExpect(status().isNotFound());
            mvc.perform(post(retry).cookie(other.cookie()).header("X-CSRF-Token", other.view().csrfToken()).header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isNotFound());
        }
    }
}
