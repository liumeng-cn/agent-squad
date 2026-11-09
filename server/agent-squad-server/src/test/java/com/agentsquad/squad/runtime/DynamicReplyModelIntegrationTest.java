package com.agentsquad.squad.runtime;

import com.agentsquad.squad.agent.tool.ReportJobResultTool;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.config.properties.WorkerModelProfileProperties;
import com.agentsquad.squad.config.properties.WorkerProfileProperties;
import com.agentsquad.squad.enums.ReasoningAdapter;
import com.agentsquad.squad.enums.ReasoningMode;
import com.agentsquad.squad.model.dto.MessageCreateDTO;
import com.agentsquad.squad.service.MessageAcceptanceService;
import com.agentsquad.squad.service.ModelCatalogService;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 动态模型选择的真实SDK集成验证
 * <p>
 * 两个独立本地HTTP模型端点证明切换实际执行路由而非只改页面文案；在途回复固定选择并使用对应预算Profile。
 */
class DynamicReplyModelIntegrationTest {
    @Test
    void exposesPublishedWorkerResultSchemaToModel() throws Exception {
        var json = new ObjectMapper();
        var schema = json.readTree("{\"type\":\"object\",\"required\":[\"poem\"],\"properties\":{\"poem\":{\"type\":\"string\"}}}");
        var tool = new ReportJobResultTool(null, json,
                1L, "session", 2L, "guard").outputSchema(schema);
        assertThat(json.valueToTree(tool.getParameters()).path("properties").path("result")).isEqualTo(schema);
    }

    @Test
    void translatesQwenThinkingWithoutSparkParameters() {
        var catalog = new ModelCatalogService();
        var profile = new WorkerModelProfileProperties();
        profile.setReasoningAdapter(ReasoningAdapter.QWEN_THINKING);
        var off = catalog.applyReasoning(io.agentscope.core.model.GenerateOptions.builder(), profile, ReasoningMode.OFF).build();
        var on = catalog.applyReasoning(io.agentscope.core.model.GenerateOptions.builder(), profile, ReasoningMode.ON).build();
        assertThat(off.getAdditionalBodyParams()).containsEntry("enable_thinking", false).doesNotContainKey("thinking");
        assertThat(on.getAdditionalBodyParams()).containsEntry("enable_thinking", true).doesNotContainKey("thinking");
        assertThatThrownBy(() -> catalog.applyReasoning(io.agentscope.core.model.GenerateOptions.builder(), profile, ReasoningMode.AUTO))
                .isInstanceOf(RuntimeBusinessException.class);
    }

    @Test
    void preservesAcceptedDefaultOnlyWhenExplicitAliasMatchesExactly() {
        var registry = new WorkerProfileProperties();
        var previous = new WorkerModelProfileProperties();
        previous.setProvider("OPENAI_COMPATIBLE");
        previous.setModelName("previous");
        previous.setBaseUrl("http://localhost/model");
        previous.setApiKey("local-test");
        previous.setPricingVersion("test-v1");
        previous.setMaxInputTokens(1024);
        previous.setMaxOutputTokens(256);
        registry.getModelProfiles().put("default", previous);
        var catalog = new ModelCatalogService();
        org.springframework.test.util.ReflectionTestUtils.setField(catalog, "registry", registry);
        org.springframework.test.util.ReflectionTestUtils.setField(catalog, "json", new ObjectMapper());
        var accepted = catalog.accept(null, ReasoningMode.DEFAULT);
        registry.getModelProfiles().put("previous", previous);
        var replacement = new ObjectMapper().convertValue(previous, WorkerModelProfileProperties.class);
        replacement.setModelName("replacement");
        registry.getModelProfiles().put("default", replacement);
        assertThat(catalog.canonicalize(accepted).modelId()).isEqualTo("previous");
        assertThat(catalog.resolve(accepted)).isSameAs(previous);
        previous.setMaxOutputTokens(512);
        assertThatThrownBy(() -> catalog.resolve(accepted))
                .isInstanceOf(RuntimeBusinessException.class);
    }

    @Test
    void freezesSelectionWhileQueuedAndCallsChosenModel(@TempDir Path directory) throws Exception {
        Long user = 1L;
        try (var firstModel = new NaturalReplyModelStub(); var secondModel = new NaturalReplyModelStub();
             var context = SpringApplication.run(AgentSquadApplication.class,
                     "--spring.main.web-application-type=none", "--spring.datasource.url=" + PostgresTestDatabase.url(directory),
                     "--spring.datasource.username=" + PostgresTestDatabase.username(), "--spring.datasource.password=" + PostgresTestDatabase.password(),
                     "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=100",
                     "--agentsquad.projection.poll-delay-ms=100", "--agentsquad.model.api-key=first-only",
                     "--agentsquad.model.base-url=" + firstModel.baseUrl(), "--agentsquad.model.name=first-model",
                     "--agentsquad.worker.model-profiles.default.api-key=first-only",
                     "--agentsquad.worker.model-profiles.default.base-url=" + firstModel.baseUrl(),
                     "--agentsquad.worker.model-profiles.default.model-name=first-model",
                     "--agentsquad.worker.model-profiles.default.reasoning-adapter=SPARK_THINKING",
                     "--agentsquad.worker.model-profiles.default.reasoning-modes=OFF,AUTO,ON",
                     "--agentsquad.worker.model-profiles.default.default-reasoning-mode=AUTO",
                     "--agentsquad.worker.model-profiles.second.provider=OPENAI_COMPATIBLE",
                     "--agentsquad.worker.model-profiles.second.api-key=second-only",
                     "--agentsquad.worker.model-profiles.second.base-url=" + secondModel.baseUrl(),
                     "--agentsquad.worker.model-profiles.second.model-name=second-model",
                     "--agentsquad.worker.model-profiles.second.reasoning-adapter=SPARK_THINKING",
                     "--agentsquad.worker.model-profiles.second.reasoning-modes=DEFAULT,ON",
                     "--agentsquad.worker.model-profiles.second.default-reasoning-mode=ON",
                     "--agentsquad.worker.model-profiles.second.pricing-version=test-v1",
                     "--agentsquad.worker.model-profiles.second.input-microunits-per-million-tokens=1000000",
                     "--agentsquad.worker.model-profiles.second.output-microunits-per-million-tokens=1000000",
                     "--agentsquad.worker.model-profiles.second.max-input-tokens=8192",
                     "--agentsquad.worker.model-profiles.second.max-output-tokens=2048")) {
            var messages = context.getBean(MessageAcceptanceService.class);
            var catalog = context.getBean(ModelCatalogService.class);
            var json = new ObjectMapper();
            assertThat(catalog.list()).hasSize(2);
            assertThat(json.writeValueAsString(catalog.list())).doesNotContain("first-only", "second-only", firstModel.baseUrl());
            var first = messages.accept(user, null, "first-choice", new MessageCreateDTO("第一条", List.of(), "default", ReasoningMode.OFF));
            assertThat(firstModel.entered.await(10, TimeUnit.SECONDS)).isTrue();
            var next = messages.accept(user, first.conversationId(), "next-choice", new MessageCreateDTO("第二条", List.of(), "second", ReasoningMode.ON));
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT model_selection_json FROM sq_response_execution WHERE execution_id=?", String.class, first.executionId())).contains("default", "OFF").doesNotContain("second-only", "first-only");
            firstModel.firstChunk.countDown();
            firstModel.release.countDown();
            secondModel.firstChunk.countDown();
            secondModel.release.countDown();
            ResponseNativeExecutionIntegrationTest.await(context, first, "SUCCEEDED");
            ResponseNativeExecutionIntegrationTest.await(context, next, "SUCCEEDED");
            assertThat(firstModel.calls.get()).isEqualTo(1);
            assertThat(secondModel.calls.get()).isEqualTo(1);
            var firstRequest = json.readTree(firstModel.requests.getFirst());
            var secondRequest = json.readTree(secondModel.requests.getFirst());
            assertThat(firstRequest.path("model").asText()).isEqualTo("first-model");
            assertThat(firstRequest.path("thinking").path("type").asText()).isEqualTo("disabled");
            assertThat(secondRequest.path("model").asText()).isEqualTo("second-model");
            assertThat(secondRequest.path("thinking").path("type").asText()).isEqualTo("enabled");
            assertThat(jdbc.queryForList("SELECT DISTINCT model_profile_key FROM sq_budget_reservation WHERE user_id=?", String.class, user)).containsExactlyInAnyOrder("default", "second");
            assertThatThrownBy(() -> messages.accept(user, first.conversationId(), "next-choice", new MessageCreateDTO("第二条", List.of(), "default", ReasoningMode.OFF))).isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> catalog.accept("second", ReasoningMode.HIGH)).isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> catalog.accept("missing", ReasoningMode.DEFAULT)).isInstanceOf(RuntimeBusinessException.class);
            var choice = catalog.accept("second", ReasoningMode.DEFAULT);
            var registry = context.getBean(WorkerProfileProperties.class);
            registry.getModelProfiles().get("second").setApiKey("rotated-key");
            assertThat(catalog.resolve(choice).getModelName()).isEqualTo("second-model");
            registry.getModelProfiles().get("second").setModelName("silently-replaced");
            assertThatThrownBy(() -> catalog.resolve(choice)).isInstanceOf(RuntimeBusinessException.class);
        }
    }
}
