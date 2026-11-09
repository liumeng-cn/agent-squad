package com.agentsquad.squad.runtime;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.agentsquad.squad.converter.ModelFailureClassifier;
import com.agentsquad.squad.exceptions.PlanCorrectionExhaustedException;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.mapper.ExecutionFailureMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.model.bo.PrepareRequestBO;
import com.agentsquad.squad.model.dto.MessageCreateDTO;
import com.agentsquad.squad.service.*;
import io.agentscope.extensions.model.openai.exception.OpenAIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 故障分类、持久次数和显式后继重试的集成验证。
 * <p>
 * 不靠异常文案猜测权限，未知账本不重发；后继保留原消息、旧终态及共享预算。
 */
class ExecutionFailureIntegrationTest {
    @Test
    void classifiesProtocolAndTypeWithoutInspectingMessages() {
        assertThat(ModelFailureClassifier.classify(new RuntimeException("401 permission denied 超时")).category()).isEqualTo("INTERNAL");
        var auth = ModelFailureClassifier.classify(new OpenAIException("private-secret", 401, "secret-code", "secret-body"));
        assertThat(auth.category()).isEqualTo("AUTH_REQUIRED");
        assertThat(auth.actor()).isEqualTo("SYSTEM");
        assertThat(auth.safeMessage()).doesNotContain("secret");
        var billing = ModelFailureClassifier.classify(new RuntimeException("wrapped",
                new OpenAIException("private-secret", 402, "secret-code", "secret-body")));
        assertThat(billing.category()).isEqualTo("ENVIRONMENT");
        assertThat(billing.code()).isEqualTo("MODEL_BILLING_UNAVAILABLE");
        assertThat(billing.actor()).isEqualTo("SYSTEM");
        assertThat(billing.safeMessage()).contains("服务暂时异常").doesNotContain("secret", "余额", "计费", "校验");
        assertThat(ModelFailureClassifier.classify(new OpenAIException("x", 503, "x", "x")).category()).isEqualTo("SERVICE_TRANSIENT");
        assertThat(ModelFailureClassifier.classify(new OpenAIException("x", 404, "x", "x")).category()).isEqualTo("ENVIRONMENT");
        assertThat(ModelFailureClassifier.classify(new OpenAIException("x", 422, "x", "x")).category()).isEqualTo("LOGIC_INVALID");
        assertThat(ModelFailureClassifier.classify(new java.io.EOFException("local-file")).category()).isEqualTo("INTERNAL");
        var interrupted = ModelFailureClassifier.classify(new RuntimeException("wrapper",
                new io.agentscope.core.model.transport.HttpTransportException("transport", new java.io.EOFException("private"))));
        assertThat(interrupted.code()).isEqualTo("MODEL_CONNECTION_INTERRUPTED");
        assertThat(interrupted.safeMessage()).doesNotContain("private");
        // 百炼把欠费与未开通放在 HTTP 400 的错误码里，要归类为环境故障而不是请求校验失败。
        var arrearage = ModelFailureClassifier.classify(new OpenAIException("private-secret", 400, "Arrearage", "hidden"));
        assertThat(arrearage.category()).isEqualTo("ENVIRONMENT");
        assertThat(arrearage.code()).isEqualTo("MODEL_BILLING_UNAVAILABLE");
        var unpurchased = ModelFailureClassifier.classify(new OpenAIException("private-secret", 400, "AccessDenied.Unpurchased", "hidden"));
        assertThat(unpurchased.category()).isEqualTo("ENVIRONMENT");
        assertThat(unpurchased.code()).isEqualTo("MODEL_ACCESS_DENIED");
        var transportBilling = ModelFailureClassifier.classify(new io.agentscope.core.model.transport.HttpTransportException("transport", 400,
                "{\"error\":{\"code\":\"Arrearage\",\"message\":\"hidden\"}}"));
        assertThat(transportBilling.code()).isEqualTo("MODEL_BILLING_UNAVAILABLE");
        assertThat(ModelFailureClassifier.classify(new OpenAIException("x", 400, "unknown-code", "x")).code())
                .isEqualTo("MODEL_REQUEST_REJECTED");
    }

    @Test
    void billingFailureOffersManualRetryButDoesNotAutomaticallyRetry(@TempDir Path directory) {
        try (var context = start(PostgresTestDatabase.url(directory))) {
            Long user = IdWorker.getId();
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "billing", new MessageCreateDTO("你好", List.of()));
            var jdbc = context.getBean(JdbcTemplate.class);
            String session = session(jdbc, receipt.executionId());
            Long scopeId = context.getBean(ExecutionScopeService.class).scopeForSession(user, session);
            Long binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", Long.class, receipt.executionId());
            var service = context.getBean(ExecutionFailureService.class);
            service.reportModel(user, session, "billing-call", new OpenAIException("private-secret", 402, "hidden", "hidden"));
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            assertThat(service.queueTransientRetry(scope, binding, "billing-event", "turn")).isFalse();
            jdbc.update("UPDATE sq_response_execution SET status='FAILED' WHERE execution_id=?", receipt.executionId());
            jdbc.update("UPDATE sq_command SET status='APPLIED' WHERE user_id=?", user);
            var failure = service.list(user, receipt.conversationId()).getFirst();
            assertThat(failure.allowedActions()).containsExactly("RETRY");
            assertThat(failure.safeMessage()).contains("服务暂时异常").doesNotContain("余额", "计费", "private-secret");
            var retry = context.getBean(ResponseRetryService.class).retry(user, receipt.executionId(), "billing-retry", failure.expectedVersion());
            assertThat(retry.messageId()).isEqualTo(receipt.messageId());
            assertThat(retry.executionId()).isNotEqualTo(receipt.executionId());
            assertThat(jdbc.queryForObject("SELECT model_selection_json FROM sq_response_execution WHERE execution_id=?", String.class, retry.executionId()))
                    .isEqualTo(jdbc.queryForObject("SELECT model_selection_json FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId()));
            assertThat(service.canRetry(user, receipt.executionId())).isFalse();
        }
    }

    @Test
    void retainsRetryLimitAcrossRestartAndCreatesOneSuccessor(@TempDir Path directory) {
        String database = PostgresTestDatabase.url(directory.resolve("failure"));
        Long user = IdWorker.getId();
        Long execution;
        String session;
        Long scopeId;
        Long message;
        try (var context = start(database)) {
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateDTO("请回答", List.of()));
            execution = receipt.executionId();
            message = receipt.messageId();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            session = session(jdbc, execution);
            scopeId = context.getBean(ExecutionScopeService.class).scopeForSession(user, session);
            var service = context.getBean(ExecutionFailureService.class);
            service.reportModel(user, session, "call-1", new OpenAIException("private-secret", 503, "hidden", "hidden"));
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            Long binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", Long.class, execution);
            assertThat(service.queueTransientRetry(scope, binding, "failure-one", "fixed-turn")).isTrue();
            assertThat(service.queueTransientRetry(scope, binding, "failure-one", "fixed-turn")).isTrue();
            assertThat(context.getBean(ExecutionFailureMapper.class).serviceAttempts(user, scopeId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE user_id=? AND scope_key=?", Long.class, user, "service-retry:" + scopeId)).isEqualTo(1);
        }
        try (var context = start(database)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var service = context.getBean(ExecutionFailureService.class);
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            Long binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", Long.class, execution);
            assertThat(service.queueTransientRetry(scope, binding, "failure-two", "fixed-turn")).isTrue();
            assertThat(service.queueTransientRetry(scope, binding, "failure-three", "fixed-turn")).isFalse();
            assertThat(context.getBean(ExecutionFailureMapper.class).serviceAttempts(user, scopeId)).isEqualTo(2);
            assertThat(context.getBean(ExecutionFailureMapper.class).nextAttempt(user, scopeId)).isNotNull();
            jdbc.update("UPDATE sq_response_execution SET status='FAILED' WHERE execution_id=?", execution);
            jdbc.update("UPDATE sq_command SET status='APPLIED' WHERE user_id=?", user);
            long version = jdbc.queryForObject("SELECT version FROM sq_response_execution WHERE execution_id=?", Long.class, execution);
            var retries = context.getBean(ResponseRetryService.class);
            var successor = retries.retry(user, execution, "retry", version);
            assertThat(retries.retry(user, execution, "retry", version)).isEqualTo(successor);
            assertThat(successor.messageId()).isEqualTo(message);
            assertThat(successor.executionId()).isNotEqualTo(execution);
            assertThatThrownBy(() -> retries.retry(user, execution, "other-key", version)).isInstanceOf(RuntimeBusinessException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, execution)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE user_id=?", Long.class, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account WHERE user_id=?", Long.class, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT account_id) FROM sq_execution_scope WHERE user_id=?", Long.class, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT dispatch_sequence FROM sq_response_execution WHERE execution_id=?", Long.class, successor.executionId())).isGreaterThan(jdbc.queryForObject("SELECT dispatch_sequence FROM sq_response_execution WHERE execution_id=?", Long.class, execution));
        }
    }

    @Test
    void unknownWriteBlocksAutomaticAndManualRetry(@TempDir Path directory) {
        try (var context = start(PostgresTestDatabase.url(directory.resolve("unknown")))) {
            Long user = IdWorker.getId();
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateDTO("处理这项工作", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String session = session(jdbc, receipt.executionId());
            var scopeService = context.getBean(ExecutionScopeService.class);
            Long scopeId = scopeService.scopeForSession(user, session);
            var operation = context.getBean(OperationService.class).prepare(new PrepareRequestBO(user, session, "turn", "write", "mock_ticket_create", "v1", "WRITE", Map.of("title", "test"), "stable", false, null, 0, 1, "tester"));
            jdbc.update("UPDATE sq_operation SET status='UNKNOWN' WHERE operation_id=?", operation.getOperationId());
            var failures = context.getBean(ExecutionFailureService.class);
            failures.reportModel(user, session, "error", new OpenAIException("private-secret", 503, "x", "x"));
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            Long binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", Long.class, receipt.executionId());
            assertThat(failures.queueTransientRetry(scope, binding, "failed", "fixed-turn")).isFalse();
            jdbc.update("UPDATE sq_response_execution SET status='FAILED' WHERE execution_id=?", receipt.executionId());
            var snapshot = context.getBean(ConversationQueryService.class).snapshot(user, receipt.conversationId(), null, 40);
            assertThat(snapshot.operationIds()).containsExactly(operation.getOperationId());
            assertThat(snapshot.failures().getFirst().allowedActions()).isEmpty();
            assertThat(snapshot.failures().toString()).doesNotContain("private-secret");
            assertThatThrownBy(() -> context.getBean(ResponseRetryService.class).retry(user, receipt.executionId(), "retry", 0)).isInstanceOf(RuntimeBusinessException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, operation.getOperationId())).isEqualTo("UNKNOWN");
            assertThat(context.getBean(ExecutionFailureMapper.class).serviceAttempts(user, scopeId)).isZero();
        }
    }

    @Test
    void invalidPlanLimitSurvivesRestart(@TempDir Path directory) {
        String database = PostgresTestDatabase.url(directory.resolve("plan-limit"));
        Long user = IdWorker.getId();
        String session;
        Long scopeId;
        try (var context = start(database)) {
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "plan-check", new MessageCreateDTO("验证纠正边界", List.of()));
            session = session(context.getBean(JdbcTemplate.class), receipt.executionId());
            scopeId = context.getBean(ExecutionScopeService.class).scopeForSession(user, session);
            var service = context.getBean(ExecutionFailureService.class);
            assertThat(service.invalidPlan(user, session)).isTrue();
            assertThat(service.invalidPlan(user, session)).isTrue();
            assertThat(service.invalidPlan(user, session)).isFalse();
        }
        try (var context = start(database)) {
            assertThat(context.getBean(ExecutionFailureMapper.class).planAttempts(user, scopeId)).isEqualTo(3);
            var service = context.getBean(ExecutionFailureService.class);
            assertThatThrownBy(() -> service.requireModelAllowed(user, session))
                    .isInstanceOf(PlanCorrectionExhaustedException.class);
            // 同一原因已由计划纠正记录，模型分类再次上报不能生成第二条用户可见故障。
            service.reportModel(user, session, "plan-exhausted-call",
                    new PlanCorrectionExhaustedException());
            assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT COUNT(1) FROM sq_execution_failure WHERE user_id=? AND scope_id=? AND code='PLAN_CORRECTION_EXHAUSTED'",
                    Integer.class, user, scopeId)).isEqualTo(1);
        }
    }

    private static String session(JdbcTemplate jdbc, Long execution) {
        return jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=(SELECT binding_id FROM sq_response_execution WHERE execution_id=?)", String.class, execution);
    }

    private static ConfigurableApplicationContext start(String database) {
        return MessageAcceptanceHttpTest.start(database, PostgresTestDatabase.username(), PostgresTestDatabase.password(), IdWorker.getId());
    }
}
