package com.agentsquad.squad.runtime;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.enums.ApiCode;
import com.agentsquad.squad.testsupport.ApiResultJson;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.testsupport.TestAssertion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对话新增接口的HTTP安全与代理联调回归。
 * 验证跨用户、游标、标题版本和写入身份，包含开发代理携带Origin的PATCH请求。
 */
class ConversationApiSecurityTest {
    @Test
    void readRenameAndRetryRequireCorrectIdentityAndVersion(@TempDir Path directory) throws Exception {
        try (var context = MessageAcceptanceHttpTest.start(PostgresTestDatabase.url(directory.resolve("api")), PostgresTestDatabase.username(), PostgresTestDatabase.password(), IdWorker.getId())) {
            var actor = MessageAcceptanceHttpTest.actor(context);
            var receipt = MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/messages", "first", Map.of("text", "接口验证"));
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            String base = "/api/v1/conversations/" + receipt.conversationId();
            mvc.perform(get(base + "/snapshot")).andExpect(status().isUnauthorized());
            mvc.perform(get(base + "/snapshot").with(TestAssertion.as(actor.view().userId(), "USER"))).andExpect(status().isOk());
            String invalidAfter = mvc.perform(get(base + "/events").with(TestAssertion.as(actor.view().userId(), "USER")).param("after", "invalid"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, invalidAfter)).isEqualTo(ApiCode.BAD_REQUEST.getCode());
            String foreignCursor = mvc.perform(get(base + "/events").with(TestAssertion.as(actor.view().userId(), "USER")).param("after", receipt.conversationId() + ":1:999999"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, foreignCursor)).isEqualTo(ApiCode.BAD_REQUEST.getCode());
            String body = "{\"title\":\"新的标题\",\"expectedTitleVersion\":0}";
            mvc.perform(patch(base + "/title").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
            mvc.perform(patch(base + "/title").with(TestAssertion.as(actor.view().userId(), "USER")).header("Origin", "http://127.0.0.1:5174").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
            String staleTitle = mvc.perform(patch(base + "/title").with(TestAssertion.as(actor.view().userId(), "USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, staleTitle)).isEqualTo(ApiCode.CONFLICT.getCode());
            String retry = "/api/v1/executions/" + receipt.executionId() + "/retry";
            mvc.perform(post(retry).header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isUnauthorized());
            String staleRetry = mvc.perform(post(retry).with(TestAssertion.as(actor.view().userId(), "USER")).header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, staleRetry)).isEqualTo(ApiCode.CONFLICT.getCode());
            var other = new MessageTestActor(new AuthView(IdWorker.getId(), "other", java.util.List.of(AccountRole.USER), null));
            String foreignSnapshot = mvc.perform(get(base + "/snapshot").with(TestAssertion.as(other.view().userId(), "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, foreignSnapshot)).isEqualTo(ApiCode.NOT_FOUND.getCode());
            String adminSnapshot = mvc.perform(get(base + "/snapshot").with(TestAssertion.as(other.view().userId(), "ADMIN")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, adminSnapshot)).isEqualTo(ApiCode.NOT_FOUND.getCode());
            String foreignEvents = mvc.perform(get(base + "/events").with(TestAssertion.as(other.view().userId(), "USER")).param("after", receipt.conversationId() + ":1:0"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, foreignEvents)).isEqualTo(ApiCode.NOT_FOUND.getCode());
            String adminEvents = mvc.perform(get(base + "/events").with(TestAssertion.as(other.view().userId(), "ADMIN")).param("after", receipt.conversationId() + ":1:0"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, adminEvents)).isEqualTo(ApiCode.NOT_FOUND.getCode());
            String foreignRetry = mvc.perform(post(retry).with(TestAssertion.as(other.view().userId(), "USER")).header("Idempotency-Key", "retry").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(mapper, foreignRetry)).isEqualTo(ApiCode.NOT_FOUND.getCode());
        }
    }
}
