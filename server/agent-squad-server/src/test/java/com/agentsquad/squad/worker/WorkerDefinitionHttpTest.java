package com.agentsquad.squad.worker;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.agentsquad.squad.worker.service.WorkerDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            String role = context.getEnvironment().getProperty("agentsquad.auth.role", "ADMIN");

            String createdBody = mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            JsonNode created = mapper.readTree(createdBody);
            String definitionId = created.get("definitionId").asText();
            assertThat(created.get("status").asText()).isEqualTo("ENABLED");
            assertThat(created.get("version").asLong()).isZero();
            WorkerDefinitionService definitions = context.getBean(WorkerDefinitionService.class);
            assertThat(definitions.listEnabled(null, 10).items())
                    .extracting("workerKey").containsExactly("log-analysis");
            assertThat(definitions.requireEnabled("log-analysis", "1.0.0").definitionId())
                    .isEqualTo(definitionId);

            String repeatedBody = mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(repeatedBody).get("definitionId").asText()).isEqualTo(definitionId);

            mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(DEFINITION.replace("日志分析", "其他描述")))
                    .andExpect(status().isConflict());

            mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "publish-2")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isConflict());

            mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "publish-review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(DEFINITION.replace("log-analysis", "draft-review")
                                    .replace("日志分析", "草稿审阅")))
                    .andExpect(status().isCreated());
            assertThat(definitions.listEnabled(null, 10).items())
                    .extracting("workerKey").containsExactly("log-analysis", "draft-review");
            assertThat(definitions.requireEnabled("draft-review", "1.0.0").description())
                    .isEqualTo("草稿审阅");

            String listBody = mvc.perform(get("/api/v1/worker-definitions").header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(listBody).get("items").size()).isEqualTo(2);

            String disabledBody = mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "status-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(disabledBody).get("version").asLong()).isEqualTo(1);
            assertThat(definitions.listEnabled(null, 10).items())
                    .extracting("workerKey").containsExactly("draft-review");
            assertThatThrownBy(() -> definitions.requireEnabled("log-analysis", "1.0.0"))
                    .isInstanceOf(RuntimeBusinessException.class);

            String repeatedStatus = mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "status-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(repeatedStatus).get("version").asLong()).isEqualTo(1);

            mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "status-2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"status\":\"ENABLED\"}"))
                    .andExpect(status().isConflict());

            mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "status-3")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":1,\"status\":\"REVOKED\"}"))
                    .andExpect(status().isOk());

            mvc.perform(patch("/api/v1/worker-definitions/{id}", definitionId)
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
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
            String role = context.getEnvironment().getProperty("agentsquad.auth.role", "ADMIN");

            mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "publish-1")
                            .contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void resolvesConfiguredProfilesAndRejectsUnknownReferences(@TempDir Path tempDirectory) throws Exception {
        try (ConfigurableApplicationContext context = start(tempDirectory, "ADMIN",
                "--agentsquad.worker.model-profiles.review.provider=OPENAI_COMPATIBLE",
                "--agentsquad.worker.model-profiles.review.model-name=review-fixture",
                "--agentsquad.worker.model-profiles.review.base-url=http://127.0.0.1:1",
                "--agentsquad.worker.model-profiles.review.api-key=test-placeholder",
                "--agentsquad.worker.model-profiles.review.pricing-version=fixture-v1",
                "--agentsquad.worker.model-profiles.review.max-input-tokens=4096",
                "--agentsquad.worker.model-profiles.review.max-output-tokens=1024",
                "--agentsquad.worker.runtime-profiles.review.max-model-calls=5",
                "--agentsquad.worker.runtime-profiles.review.max-tool-calls=2",
                "--agentsquad.worker.runtime-profiles.review.max-recovery-attempts=1",
                "--agentsquad.worker.runtime-profiles.review.model-request-timeout-seconds=30",
                "--agentsquad.worker.runtime-profiles.review.tool-timeout-seconds=10")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            String role = context.getEnvironment().getProperty("agentsquad.auth.role", "ADMIN");
            String reviewDefinition = DEFINITION.replace("default", "review");

            mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "review-profile")
                            .contentType(MediaType.APPLICATION_JSON).content(reviewDefinition))
                    .andExpect(status().isCreated());
            WorkerProfileRegistry profiles = context.getBean(WorkerProfileRegistry.class);
            assertThat(profiles.requireExecutableModel("review").getModelName())
                    .isEqualTo("review-fixture");
            assertThat(profiles.requireRuntime("review").getMaxModelCalls()).isEqualTo(5);

            mvc.perform(post("/api/v1/worker-definitions")
                            .header("X-User-Id", "20000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                            .header("Idempotency-Key", "unknown-profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(DEFINITION.replace("default", "missing")))
                    .andExpect(status().isBadRequest());
        }
    }

    private ConfigurableApplicationContext start(Path directory, String role, String... overrides) {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("squad"));
        List<String> properties = new ArrayList<>(List.of(
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.auth.username=local-user",
                "--agentsquad.auth.password=local-password",
                "--agentsquad.auth.role=" + role));
        properties.addAll(List.of(overrides));
        return SpringApplication.run(AgentSquadApplication.class, properties.toArray(String[]::new));
    }

}
