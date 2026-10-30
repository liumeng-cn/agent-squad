package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.artifact.service.ArtifactService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.governance.service.BudgetService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.integration.agentscope.ModelBudgetMiddleware;
import com.agentsquad.squad.interaction.service.InteractionService;
import com.agentsquad.squad.interaction.service.InteractionQueryService;
import com.agentsquad.squad.tools.operation.OperationExecutor;
import com.agentsquad.squad.tools.operation.OperationService;
import com.agentsquad.squad.tools.operation.PrepareRequest;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
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
import reactor.core.publisher.Flux;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 无GoalTask的共享执行边界集成验证。
 * <p>
 * 用真实平台数据及SDK模型事件验证预算、交互、审批、附件与停止屏障；不调用供应商模型，不冒充普通回复端到端验收。
 */
class ScopeRuntimeOwnershipIntegrationTest {

    @Test
    void responseCanUseGovernedCapabilitiesWithoutTask(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(databaseUrl(directory), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), 50000000)) {
            verifyCapabilitiesWithoutTask(context);
        }
    }

    @Test
    void concurrentScopesCannotExceedUserBudget(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(databaseUrl(directory), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), 100)) {
            verifyConcurrentUserBudget(context);
        }
    }

    @Test
    void sharedAccountAndUnknownChargeSurviveScopeChanges(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(databaseUrl(directory), com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), 1000)) {
            verifySharedAccount(context);
        }
    }

    static ConfigurableApplicationContext start(String url, String username, String password, long userLimit) {
        return SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password,
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=3600000",
                "--agentsquad.projection.poll-delay-ms=3600000", "--agentsquad.conversation.cleanup.delay-ms=3600000",
                "--agentsquad.budget.max-microunits-per-user=" + userLimit,
                "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=http://127.0.0.1:9/v1",
                "--agentsquad.model.name=test-model",
                "--agentsquad.worker.model-profiles.default.api-key=test-only",
                "--agentsquad.worker.model-profiles.default.base-url=http://127.0.0.1:9/v1",
                "--agentsquad.worker.model-profiles.default.model-name=test-model",
                "--agentsquad.worker.model-profiles.default.max-input-tokens=10",
                "--agentsquad.worker.model-profiles.default.max-output-tokens=10");
    }

    static void verifyCapabilitiesWithoutTask(ConfigurableApplicationContext context) {
        String userId = UUID.randomUUID().toString();
        ScopeTestExecution execution = response(context, userId, null, null, 3000000);
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        ArtifactService artifacts = context.getBean(ArtifactService.class);
        byte[] content = "回复附件".getBytes(StandardCharsets.UTF_8);
        String artifact = artifacts.upload(userId, UUID.randomUUID().toString(), "source.txt", content).artifactId();
        artifacts.attachScope(userId, execution.scopeId(), List.of(artifact));
        assertThat(artifacts.listScope(userId, execution.scopeId())).extracting("artifactId").containsExactly(artifact);
        assertThat(artifacts.content(userId, artifact).content()).isEqualTo(content);
        assertThatThrownBy(() -> artifacts.content(UUID.randomUUID().toString(), artifact))
                .isInstanceOf(RuntimeBusinessException.class);

        RuntimeContext nativeContext = RuntimeContext.builder().userId(userId).sessionId(execution.sessionId()).build();
        nativeContext.put(SessionRecorder.TURN_ID_KEY, "fixed-scope-turn");
        context.getBean(ModelBudgetMiddleware.class).onModelCall(null, nativeContext,
                new ModelCallInput(null, null, GenerateOptions.builder().maxTokens(10).build(), null),
                ignored -> Flux.just(new ModelCallEndEvent("answer", new ChatUsage(3, 2, 0)))).blockLast();
        assertThat(jdbc.queryForObject("SELECT settled_units FROM sq_budget_reservation WHERE user_id = ?",
                Long.class, userId)).isEqualTo(5);

        OperationService operations = context.getBean(OperationService.class);
        OperationExecutor executor = context.getBean(OperationExecutor.class);
        OperationRow read = operations.prepare(request(execution, "read-1", "mock_lookup", "READ", null, false, 2));
        assertThat(read.taskId()).isNull();
        assertThat(read.scopeId()).isEqualTo(execution.scopeId());
        assertThat(executor.execute(read, key -> "{\"found\":true}")).contains("found");
        operations.settle(read, 2);
        PrepareRequest anotherTurn = new PrepareRequest(userId, execution.sessionId(), "another-turn", "read-1",
                "mock_lookup", "v1", "READ", Map.of("title", "受控测试", "detail", "仅本地模拟系统"),
                null, false, null, 0, 2, "tester");
        OperationRow secondRead = operations.prepare(anotherTurn);
        assertThat(secondRead.budgetUsageKey()).isNotEqualTo(read.budgetUsageKey());
        assertThat(secondRead.operationId()).isNotEqualTo(read.operationId());
        executor.execute(secondRead, key -> "{}");
        operations.settle(secondRead, 2);


        OperationRow write = operations.prepare(request(execution, "approve-1", "mock_ticket_create", "WRITE", "intent-1", true, 1000000));
        assertThat(operations.prepare(request(execution, "other-call", "mock_ticket_create", "WRITE", "intent-1", true, 1000000)).operationId())
                .isEqualTo(write.operationId());
        InteractionService interactions = context.getBean(InteractionService.class);
        var approval = interactions.bindFrameworkRequest(userId, null, execution.bindingId(), null,
                "fixed-scope-turn", "approve-1", Map.of("name", "mock_ticket_create"));
        jdbc.update("UPDATE sq_response_execution SET status = 'WAITING_APPROVAL' WHERE execution_id = ?", execution.executionId());
        AuthView actor = new AuthView(userId, "tester", List.of("USER"), "csrf");
        var approved = interactions.respond(actor, approval.interactionId(), "approval-key", 0,
                "APPROVAL", true, write.requestHash(), null);
        assertThat(approved.taskId()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id = ?", String.class, execution.executionId())).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_mock_ticket WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);

        var input = interactions.bindFrameworkRequest(userId, null, execution.bindingId(), null,
                "fixed-scope-turn", "input-1", Map.of("name", "request_input", "input", Map.of("question", "需要什么文风？")));
        assertThat(context.getBean(InteractionQueryService.class).listOpenByScope(userId, execution.scopeId()))
                .extracting("interactionId").containsExactly(input.interactionId());
        jdbc.update("UPDATE sq_response_execution SET status = 'WAITING_INPUT' WHERE execution_id = ?", execution.executionId());
        var inputReceipt = interactions.respond(actor, input.interactionId(), "input-key", 0,
                "INPUT", null, null, Map.of("answer", "正式"));
        assertThat(interactions.respond(actor, input.interactionId(), "input-key", 0,
                "INPUT", null, null, Map.of("answer", "正式")).commandId()).isEqualTo(inputReceipt.commandId());
        assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id = ?", String.class, execution.executionId())).isEqualTo("QUEUED");
        jdbc.update("UPDATE sq_command SET due_at = '2099-01-01' WHERE user_id = ?", userId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE user_id = ? AND scope_id = ? AND task_id IS NULL",
                Integer.class, userId, execution.scopeId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task WHERE user_id = ?", Integer.class, userId)).isZero();

        OperationRow stopped = operations.prepare(request(execution, "stop-1", "mock_lookup", "READ", null, false, 2));
        // 仅验证受控执行器的未知结果边界，不把连接器异常误记成“确定未执行”。
        OperationRow uncertain = operations.prepare(request(execution, "uncertain-1", "mock_ticket_create", "WRITE", "intent-uncertain", true, 1000000));
        AtomicInteger uncertainCalls = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(uncertain, key -> {
            uncertainCalls.incrementAndGet();
            throw new IllegalStateException("sensitive-connector-error");
        })).isInstanceOf(RuntimeBusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id = ?", String.class, uncertain.operationId()))
                .isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> executor.execute(uncertain, key -> {
            uncertainCalls.incrementAndGet();
            return "{}";
        })).isInstanceOf(RuntimeBusinessException.class);
        assertThat(uncertainCalls).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT status FROM sq_budget_reservation WHERE scope_id = ? AND usage_key = ?",
                String.class, execution.scopeId(), uncertain.budgetUsageKey())).isEqualTo("RESERVED");

        assertThatThrownBy(() -> operations.prepare(request(execution, "blocked-by-unknown", "mock_lookup", "READ", null, false, 1)))
                .isInstanceOf(RuntimeBusinessException.class);
        AtomicInteger blockedDispatch = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(stopped, key -> {
            blockedDispatch.incrementAndGet();
            return "{}";
        })).isInstanceOf(RuntimeBusinessException.class);
        assertThat(blockedDispatch).hasValue(0);
        jdbc.update("UPDATE sq_response_execution SET status = 'CANCELLING', cancel_requested_at = CURRENT_TIMESTAMP WHERE execution_id = ?", execution.executionId());
        AtomicInteger externalCalls = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(stopped, key -> {
            externalCalls.incrementAndGet();
            return "{}";
        })).isInstanceOf(RuntimeBusinessException.class);
        assertThat(externalCalls).hasValue(0);
        assertThatThrownBy(() -> context.getBean(BudgetService.class).reserve(userId, execution.scopeId(), "after-stop", "TOOL", 1, "tester"))
                .isInstanceOf(RuntimeBusinessException.class);
        operations.settle(stopped, 0);
        jdbc.update("UPDATE sq_response_execution SET del_flag = '1' WHERE execution_id = ?", execution.executionId());
        assertThatThrownBy(() -> artifacts.content(userId, artifact)).isInstanceOf(RuntimeBusinessException.class);
        jdbc.update("UPDATE sq_response_execution SET del_flag = '0' WHERE execution_id = ?", execution.executionId());
        jdbc.update("UPDATE sq_conversation SET generation = generation + 1 WHERE user_id = ? AND conversation_id = ?", userId, execution.conversationId());
        assertThatThrownBy(() -> artifacts.content(userId, artifact)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> interactions.respond(actor, input.interactionId(), "input-key", 0,
                "INPUT", null, null, Map.of("answer", "正式"))).isInstanceOf(RuntimeBusinessException.class);
    }

    static void verifyConcurrentUserBudget(ConfigurableApplicationContext context) throws Exception {
        String userId = UUID.randomUUID().toString();
        ScopeTestExecution first = response(context, userId, null, null, 100);
        ScopeTestExecution second = response(context, userId, null, null, 100);
        BudgetService budget = context.getBean(BudgetService.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var a = threads.submit(() -> reserveWithSnapshot(context, budget, first, ready, start));
            var b = threads.submit(() -> reserveWithSnapshot(context, budget, second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat((a.get(15, TimeUnit.SECONDS) ? 1 : 0) + (b.get(15, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        }
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT SUM(reserved_units) FROM sq_budget_reservation WHERE user_id = ?", Long.class, userId)).isEqualTo(60);
    }

    static void verifySharedAccount(ConfigurableApplicationContext context) {
        String userId = UUID.randomUUID().toString();
        ScopeTestExecution first = response(context, userId, null, null, 100);
        ScopeTestExecution second = response(context, userId, first.conversationId(), first.accountId(), 100);
        BudgetService budget = context.getBean(BudgetService.class);
        budget.reserve(userId, first.scopeId(), "one", "TOOL", 70, "tester");
        assertThatThrownBy(() -> budget.reserve(userId, second.scopeId(), "two", "TOOL", 40, "tester"))
                .isInstanceOf(RuntimeBusinessException.class);
        budget.reserveModel(userId, second.scopeId(), "unknown-model", "a".repeat(64), "default",
                context.getBean(com.agentsquad.squad.worker.profile.WorkerProfileRegistry.class).requireExecutableModel("default"));
        budget.markModelUnknown(userId, second.scopeId(), "unknown-model");
        assertThatThrownBy(() -> budget.reserve(userId, first.scopeId(), "three", "TOOL", 11, "tester"))
                .isInstanceOf(RuntimeBusinessException.class);
        budget.settle(userId, first.scopeId(), "one", 20);
        budget.reserve(userId, second.scopeId(), "four", "TOOL", 60, "tester");
        assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT SUM(CASE WHEN status = 'SETTLED' THEN settled_units ELSE reserved_units END) FROM sq_budget_reservation WHERE user_id = ?",
                Long.class, userId)).isEqualTo(100);
    }

    /**
     * 两个事务先建立读快照再竞争总账，验证MySQL可重复读下不能沿用锁前旧余额。
     */
    private static boolean reserveWithSnapshot(ConfigurableApplicationContext context, BudgetService budget,
                                               ScopeTestExecution execution, CountDownLatch ready, CountDownLatch start) {
        TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        return Boolean.TRUE.equals(transaction.execute(status -> {
            context.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT COUNT(*) FROM sq_budget_reservation WHERE user_id = ?", Long.class, execution.userId());
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发启动超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("并发验收中断", exception);
            }
            try {
                budget.reserve(execution.userId(), execution.scopeId(), "parallel", "TOOL", 60, "tester");
                return true;
            } catch (RuntimeBusinessException exception) {
                status.setRollbackOnly();
                return false;
            }
        }));
    }

    private static ScopeTestExecution response(ConfigurableApplicationContext context, String userId,
                                               String conversationId, String accountId, long limit) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        if (conversationId == null) {
            conversationId = context.getBean(GoalTaskService.class).createConversation(userId, "回复边界").conversationId();
        }
        ExecutionScopeMapper scopes = context.getBean(ExecutionScopeMapper.class);
        if (accountId == null) {
            accountId = UUID.randomUUID().toString();
            scopes.insertAccount(userId, accountId, "STANDARD", limit);
        }
        String executionId = UUID.randomUUID().toString();
        String scopeId = UUID.randomUUID().toString();
        String bindingId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        scopes.insertScope(scopeId, userId, conversationId, 0, "RESPONSE", executionId, accountId);
        jdbc.update("UPDATE sq_conversation SET message_seq = message_seq + 1 WHERE user_id = ? AND conversation_id = ?", userId, conversationId);
        jdbc.update("""
                INSERT INTO sq_message (message_id, user_id, conversation_id, message_seq, role, content, completed, execution_id, occurred_at)
                SELECT ?, user_id, conversation_id, message_seq, 'USER', '边界验收', TRUE, ?, CURRENT_TIMESTAMP
                FROM sq_conversation WHERE user_id = ? AND conversation_id = ?
                """, messageId, executionId, userId, conversationId);
        context.getBean(BindingMapper.class).insertResponse(bindingId, userId, scopeId, "a".repeat(32), sessionId, LocalDateTime.now());
        jdbc.update("""
                INSERT INTO sq_response_execution (execution_id, user_id, conversation_id, source_message_id, scope_id, binding_id, status)
                VALUES (?, ?, ?, ?, ?, ?, 'RUNNING')
                """, executionId, userId, conversationId, messageId, scopeId, bindingId);
        return new ScopeTestExecution(userId, conversationId, executionId, scopeId, bindingId, sessionId, accountId);
    }

    private static PrepareRequest request(ScopeTestExecution execution, String callId, String tool, String effect,
                                          String intent, boolean approval, long units) {
        return new PrepareRequest(execution.userId(), execution.sessionId(), "fixed-scope-turn", callId, tool, "v1",
                effect, Map.of("title", "受控测试", "detail", "仅本地模拟系统"), intent, approval, null, 0, units, "tester");
    }

    private String databaseUrl(Path directory) {
        return com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("scope"));
    }
}
