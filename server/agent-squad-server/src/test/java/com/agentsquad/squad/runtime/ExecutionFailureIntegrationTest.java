package com.agentsquad.squad.runtime;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.conversation.service.ResponseRetryService;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.runtime.repository.ExecutionFailureMapper;
import com.agentsquad.squad.runtime.service.ExecutionFailureService;
import com.agentsquad.squad.runtime.domain.ModelFailureClassifier;
import com.agentsquad.squad.presentation.service.ConversationQueryService;
import com.agentsquad.squad.tools.operation.OperationService;
import com.agentsquad.squad.tools.operation.PrepareRequest;
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
        assertThat(ModelFailureClassifier.classify(new OpenAIException("x", 503, "x", "x")).category()).isEqualTo("SERVICE_TRANSIENT");
        assertThat(ModelFailureClassifier.classify(new OpenAIException("x", 404, "x", "x")).category()).isEqualTo("ENVIRONMENT");
        assertThat(ModelFailureClassifier.classify(new OpenAIException("x", 422, "x", "x")).category()).isEqualTo("LOGIC_INVALID");
    }

    @Test
    void retainsRetryLimitAcrossRestartAndCreatesOneSuccessor(@TempDir Path directory) {
        String database = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("failure"));
        String user = UUID.randomUUID().toString();
        String execution;
        String session;
        String scopeId;
        String message;
        try (var context = start(database)) {
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateRequest("请回答", List.of()));
            execution = receipt.executionId();
            message = receipt.messageId();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            session = session(jdbc, execution);
            scopeId = context.getBean(ExecutionScopeService.class).scopeForSession(user, session);
            var service = context.getBean(ExecutionFailureService.class);
            service.reportModel(user, session, "call-1", new OpenAIException("private-secret", 503, "hidden", "hidden"));
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, execution);
            assertThat(service.queueTransientRetry(scope, binding, "failure-one", "fixed-turn")).isTrue();
            assertThat(service.queueTransientRetry(scope, binding, "failure-one", "fixed-turn")).isTrue();
            assertThat(context.getBean(ExecutionFailureMapper.class).serviceAttempts(user, scopeId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE user_id=? AND scope_key=?", Long.class, user, "service-retry:" + scopeId)).isEqualTo(1);
        }
        try (var context = start(database)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var service = context.getBean(ExecutionFailureService.class);
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, execution);
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
        try (var context = start(com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("unknown")))) {
            String user = UUID.randomUUID().toString();
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "source", new MessageCreateRequest("处理这项工作", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String session = session(jdbc, receipt.executionId());
            var scopeService = context.getBean(ExecutionScopeService.class);
            String scopeId = scopeService.scopeForSession(user, session);
            var operation = context.getBean(OperationService.class).prepare(new PrepareRequest(user, session, "turn", "write", "mock_ticket_create", "v1", "WRITE", Map.of("title", "test"), "stable", false, null, 0, 1, "tester"));
            jdbc.update("UPDATE sq_operation SET status='UNKNOWN' WHERE operation_id=?", operation.operationId());
            var failures = context.getBean(ExecutionFailureService.class);
            failures.reportModel(user, session, "error", new OpenAIException("private-secret", 503, "x", "x"));
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            assertThat(failures.queueTransientRetry(scope, binding, "failed", "fixed-turn")).isFalse();
            jdbc.update("UPDATE sq_response_execution SET status='FAILED' WHERE execution_id=?", receipt.executionId());
            var snapshot = context.getBean(ConversationQueryService.class).snapshot(user, receipt.conversationId(), null, 40);
            assertThat(snapshot.operationIds()).containsExactly(operation.operationId());
            assertThat(snapshot.failures().getFirst().allowedActions()).isEmpty();
            assertThat(snapshot.failures().toString()).doesNotContain("private-secret");
            assertThatThrownBy(() -> context.getBean(ResponseRetryService.class).retry(user, receipt.executionId(), "retry", 0)).isInstanceOf(RuntimeBusinessException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, operation.operationId())).isEqualTo("UNKNOWN");
            assertThat(context.getBean(ExecutionFailureMapper.class).serviceAttempts(user, scopeId)).isZero();
        }
    }

    @Test
    void invalidPlanLimitSurvivesRestart(@TempDir Path directory) {
        String database = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("plan-limit"));
        String user = UUID.randomUUID().toString();
        String session;
        String scopeId;
        try (var context = start(database)) {
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "plan-check", new MessageCreateRequest("验证纠正边界", List.of()));
            session = session(context.getBean(JdbcTemplate.class), receipt.executionId());
            scopeId = context.getBean(ExecutionScopeService.class).scopeForSession(user, session);
            var service = context.getBean(ExecutionFailureService.class);
            assertThat(service.invalidPlan(user, session)).isTrue();
            assertThat(service.invalidPlan(user, session)).isTrue();
            assertThat(service.invalidPlan(user, session)).isFalse();
        }
        try (var context = start(database)) {
            assertThat(context.getBean(ExecutionFailureMapper.class).planAttempts(user, scopeId)).isEqualTo(3);
            assertThatThrownBy(() -> context.getBean(ExecutionFailureService.class).requireModelAllowed(user, session))
                    .isInstanceOf(com.agentsquad.squad.runtime.domain.PlanCorrectionExhaustedException.class);
        }
    }

    private static String session(JdbcTemplate jdbc, String execution) {
        return jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=(SELECT binding_id FROM sq_response_execution WHERE execution_id=?)", String.class, execution);
    }

    private static ConfigurableApplicationContext start(String database) {
        return MessageAcceptanceHttpTest.start(database, com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString());
    }
}
