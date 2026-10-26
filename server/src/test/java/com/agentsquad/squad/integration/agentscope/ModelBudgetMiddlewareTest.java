package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.governance.BudgetService;
import com.agentsquad.squad.governance.repository.BudgetMapper;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.session.SessionRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型预算中间件的实际 SDK 事件流边界验证。
 *
 * 使用固定原生回合和模拟模型事件，不向真实模型发送请求；检查预留、结算和重复请求拦截。
 */
class ModelBudgetMiddlewareTest {

    @Test
    void modelCallReservesBeforeSendAndSettlesOnUsage(@TempDir Path directory) {
        try (ConfigurableApplicationContext spring = start(directory)) {
            Fixture fixture = fixture(spring);
            AtomicInteger calls = new AtomicInteger();
            ModelCallInput input = new ModelCallInput(null, null,
                    GenerateOptions.builder().maxTokens(10).build(), null);

            fixture.middleware.onModelCall(null, fixture.context, input, ignored -> {
                calls.incrementAndGet();
                assertThat(fixture.jdbc.queryForObject(
                        "SELECT status FROM sq_budget_reservation WHERE task_id = ?",
                        String.class, fixture.taskId)).isEqualTo("RESERVED");
                return Flux.just(new ModelCallEndEvent("reply", new ChatUsage(3, 2, 0)));
            }).blockLast();

            assertThat(calls).hasValue(1);
            assertThat(fixture.jdbc.queryForObject(
                    "SELECT status FROM sq_budget_reservation WHERE task_id = ?",
                    String.class, fixture.taskId)).isEqualTo("SETTLED");
            assertThat(fixture.jdbc.queryForObject(
                    "SELECT settled_units FROM sq_budget_reservation WHERE task_id = ?",
                    Long.class, fixture.taskId)).isEqualTo(5L);
            assertThatThrownBy(() -> fixture.middleware.onModelCall(null, fixture.context,
                    input, ignored -> {
                        calls.incrementAndGet();
                        return Flux.empty();
                    }).blockLast()).isInstanceOf(RuntimeException.class);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void missingUsageKeepsReservationUnknown(@TempDir Path directory) {
        try (ConfigurableApplicationContext spring = start(directory)) {
            Fixture fixture = fixture(spring);
            ModelCallInput input = new ModelCallInput(null, null,
                    GenerateOptions.builder().maxTokens(10).build(), null);

            assertThatThrownBy(() -> fixture.middleware.onModelCall(null, fixture.context,
                    input, ignored -> Flux.just(new ModelCallEndEvent("reply", null)))
                    .blockLast()).isInstanceOf(RuntimeException.class);
            assertThat(fixture.jdbc.queryForObject(
                    "SELECT status FROM sq_budget_reservation WHERE task_id = ?",
                    String.class, fixture.taskId)).isEqualTo("UNKNOWN");
        }
    }

    private ConfigurableApplicationContext start(Path directory) {
        return SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("squad")
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.username=sa",
                "--spring.datasource.driver-class-name=org.h2.Driver");
    }

    private Fixture fixture(ConfigurableApplicationContext spring) {
        String userId = "00000000-0000-0000-0000-000000000001";
        GoalTaskService tasks = spring.getBean(GoalTaskService.class);
        ConversationResponse conversation = tasks.createConversation(userId, "模型预算");
        TaskReceipt task = tasks.submit(userId, conversation.conversationId(),
                "model-budget", "测试模型调用预算");
        JdbcTemplate jdbc = spring.getBean(JdbcTemplate.class);
        String sessionId = jdbc.queryForObject("""
                SELECT framework_session_id FROM sq_runtime_binding
                WHERE user_id = ? AND task_id = ?
                """, String.class, userId, task.taskId());
        RuntimeContext runtime = RuntimeContext.builder()
                .userId(userId).sessionId(sessionId).build();
        runtime.put(SessionRecorder.TURN_ID_KEY, "turn-model-budget");

        WorkerModelProfile profile = new WorkerModelProfile();
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setModelName("test-model");
        profile.setBaseUrl("http://localhost/test");
        profile.setApiKey("test-only");
        profile.setPricingVersion("test-v1");
        profile.setMaxInputTokens(10);
        profile.setMaxOutputTokens(10);
        profile.setInputMicrounitsPerMillionTokens(1000000);
        profile.setOutputMicrounitsPerMillionTokens(1000000);
        WorkerProfileRegistry profiles = spring.getBean(WorkerProfileRegistry.class);
        profiles.setModelProfiles(Map.of("default", profile));

        ModelBudgetMiddleware middleware = new ModelBudgetMiddleware();
        ReflectionTestUtils.setField(middleware, "budgetMapper", spring.getBean(BudgetMapper.class));
        ReflectionTestUtils.setField(middleware, "budgetService", spring.getBean(BudgetService.class));
        ReflectionTestUtils.setField(middleware, "profileRegistry", profiles);
        ReflectionTestUtils.setField(middleware, "objectMapper", spring.getBean(ObjectMapper.class));
        return new Fixture(middleware, runtime, jdbc, task.taskId());
    }

    private record Fixture(ModelBudgetMiddleware middleware, RuntimeContext context,
                           JdbcTemplate jdbc, String taskId) {
    }
}
