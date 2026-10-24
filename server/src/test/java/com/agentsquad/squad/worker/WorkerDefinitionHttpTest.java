package com.agentsquad.squad.worker;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Worker 能力定义的 HTTP 与持久化验收。
 * <p>
 * 覆盖发布幂等、版本冲突、状态乐观锁及撤销终态，不启动模型或 Worker。
 */
class WorkerDefinitionHttpTest {

    private static final String DEFINITION = """
            {"workerKey":"log-analysis","workerVersion":"1.0.0","description":"日志分析",
             "inputSchema":{"type":"object"},"outputSchema":{"type":"object"},
             "toolAllowlist":["mock_lookup"],"modelProfileKey":"default","runtimeProfileKey":"default"}
            """;

    @Test
    void publishesAndChangesImmutableDefinition(@TempDir Path tempDirectory) throws Exception {
        try (ConfigurableApplicationContext context = start(tempDirectory, "ADMIN")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JsonNode login = login(mvc, mapper);
            Cookie cookie = new Cookie("SQUAD_SESSION", login.get("session").asText());
            String csrf = login.get("csrfToken").asText();

            String createdBody = mvc.perform(post("/api/v1/worker-definitions")
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            JsonNode created = mapper.readTree(createdBody);
            String definitionId = created.get("definitionId").asText();
            assertThat(created.get("status").asText()).isEqualTo("ENABLED");
            assertThat(created.get("version").asLong()).isZero();

            String repeatedBody = mvc.perform(post("/api/v1/worker-definitions")
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(repeatedBody).get("definitionId").asText()).isEqualTo(definitionId);

            mvc.perform(post("/api/v1/worker-definitions")
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(DEFINITION.replace("日志分析", "其他描述")))
                    .andExpect(status().isConflict());

            mvc.perform(post("/api/v1/worker-definitions")
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "publish-2")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isConflict());

            String listBody = mvc.perform(get("/api/v1/worker-definitions").cookie(cookie))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(listBody).get("items").size()).isEqualTo(1);

            String disabledBody = mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "status-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(disabledBody).get("version").asLong()).isEqualTo(1);

            String repeatedStatus = mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "status-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(repeatedStatus).get("version").asLong()).isEqualTo(1);

            mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "status-2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"status\":\"ENABLED\"}"))
                    .andExpect(status().isConflict());

            mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "status-3")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":1,\"status\":\"REVOKED\"}"))
                    .andExpect(status().isOk());

            mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .cookie(cookie).header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "status-4")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":2,\"status\":\"ENABLED\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void rejectsPublicationFromNonAdmin(@TempDir Path tempDirectory) throws Exception {
        try (ConfigurableApplicationContext context = start(tempDirectory, "USER")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JsonNode login = login(mvc, mapper);

            mvc.perform(post("/api/v1/worker-definitions")
                            .cookie(new Cookie("SQUAD_SESSION", login.get("session").asText()))
                            .header("X-CSRF-Token", login.get("csrfToken").asText())
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isForbidden());
        }
    }

    private ConfigurableApplicationContext start(Path directory, String role) {
        String databaseUrl = "jdbc:h2:file:" + directory.resolve("squad")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        return SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--agentsquad.auth.username=local-user",
                "--agentsquad.auth.password=local-password",
                "--agentsquad.auth.role=" + role);
    }

    private JsonNode login(MockMvc mvc, ObjectMapper mapper) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"local-user\",\"password\":\"local-password\"}"))
                .andExpect(status().isOk()).andReturn();
        String cookie = result.getResponse().getCookie("SQUAD_SESSION").getValue();
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        return mapper.createObjectNode()
                .put("session", cookie)
                .put("csrfToken", response.get("csrfToken").asText());
    }
}
