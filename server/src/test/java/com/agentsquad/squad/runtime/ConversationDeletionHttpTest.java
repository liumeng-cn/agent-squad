package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会话删除 HTTP 边界测试：受理、幂等重放、版本冲突、删除后读取与提交屏障。
 */
class ConversationDeletionHttpTest {

    @Test
    void shouldAcceptDeleteAndBlockReadsAndSubmits(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = "jdbc:h2:file:" + tempDirectory.resolve("squad")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--agentsquad.auth.username=local-user",
                "--agentsquad.auth.password=local-password")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            var login = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"local-user\",\"password\":\"local-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            String session = login.getResponse().getCookie("SQUAD_SESSION").getValue();
            String csrf = mapper.readTree(login.getResponse().getContentAsString()).get("csrfToken").asText();

            var conversation = mvc.perform(post("/api/v1/conversations")
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"待删除\"}"))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode conversationJson = mapper.readTree(conversation.getResponse().getContentAsString());
            String conversationId = conversationJson.get("conversationId").asText();

            mvc.perform(delete("/api/v1/conversations/{conversationId}", conversationId)
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "delete-1")
                            .header("If-Match", "0"))
                    .andExpect(status().isAccepted());

            mvc.perform(get("/api/v1/conversations/{conversationId}", conversationId)
                            .cookie(new Cookie("SQUAD_SESSION", session)))
                    .andExpect(status().isNotFound());

            var list = mvc.perform(get("/api/v1/conversations")
                            .cookie(new Cookie("SQUAD_SESSION", session)))
                    .andExpect(status().isOk()).andReturn();
            JsonNode items = mapper.readTree(list.getResponse().getContentAsString()).get("items");
            assertThat(items).isEmpty();

            mvc.perform(post("/api/v1/conversations/{conversationId}/tasks", conversationId)
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "late-task")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"迟到的目标\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void shouldReplaySameDeleteAndRejectStaleVersion(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = "jdbc:h2:file:" + tempDirectory.resolve("squad")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--agentsquad.auth.username=local-user",
                "--agentsquad.auth.password=local-password")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            var login = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"local-user\",\"password\":\"local-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            String session = login.getResponse().getCookie("SQUAD_SESSION").getValue();
            String csrf = mapper.readTree(login.getResponse().getContentAsString()).get("csrfToken").asText();

            var conversation = mvc.perform(post("/api/v1/conversations")
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"幂等删除\"}"))
                    .andExpect(status().isCreated()).andReturn();
            String conversationId = mapper.readTree(conversation.getResponse().getContentAsString())
                    .get("conversationId").asText();

            mvc.perform(delete("/api/v1/conversations/{conversationId}", conversationId)
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "delete-1")
                            .header("If-Match", "0"))
                    .andExpect(status().isAccepted());
            mvc.perform(delete("/api/v1/conversations/{conversationId}", conversationId)
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "delete-1")
                            .header("If-Match", "0"))
                    .andExpect(status().isAccepted());

            var other = mvc.perform(post("/api/v1/conversations")
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"版本冲突\"}"))
                    .andExpect(status().isCreated()).andReturn();
            String otherId = mapper.readTree(other.getResponse().getContentAsString())
                    .get("conversationId").asText();
            mvc.perform(delete("/api/v1/conversations/{conversationId}", otherId)
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "delete-2")
                            .header("If-Match", "7"))
                    .andExpect(status().isConflict());
        }
    }
}
