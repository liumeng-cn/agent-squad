package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.observer.ExecutionTrace;
import com.agentsquad.squad.vo.MessageReceiptVO;
import io.micrometer.tracing.Tracer;
import com.agentsquad.squad.dto.MessageCreateDTO;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.service.MessageAcceptanceService;
import com.agentsquad.squad.bo.NativeEventViewBO;
import com.agentsquad.squad.service.ActivityProjectionService;
import com.agentsquad.squad.dto.ActivityHistoryDTO;
import com.agentsquad.squad.vo.ExecutionActivityVO;
import com.agentsquad.squad.service.ConversationQueryService;
import com.agentsquad.squad.bo.ProjectionTargetBO;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 活动边界与一致快照的隔离数据库验证
 * <p>
 * 人工输入已提交事实，不启动模型；验证排队不冒充执行、失败结果、并行隔离、去重与敏感内容过滤。
 */
class ActivityProjectionIntegrationTest {
    @Test
    void projectsRealBoundariesWithoutLeakingArgumentsOrReopeningCompletedCalls(@TempDir Path directory) throws Exception {
        try (var context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none", "--agentsquad.delivery.enabled=false",
                "--spring.datasource.url=" + MySqlTestDatabase.url(directory),
                "--spring.datasource.username=" + MySqlTestDatabase.username(),
                "--spring.datasource.password=" + MySqlTestDatabase.password())) {
            String user = "00000000-0000-0000-0000-000000000001";
            var tracer = context.getBean(Tracer.class);
            var parent = tracer.nextSpan().name("test.message.accept").start();
            MessageReceiptVO receipt;
            try (var ignored = tracer.withSpan(parent)) {
                receipt = context.getBean(MessageAcceptanceService.class)
                        .accept(user, null, "activity", new MessageCreateDTO("读取资料", List.of()));
            } finally {
                parent.end();
            }
            var tracing = context.getBean(ExecutionTrace.class);
            assertThat(tracer.currentSpan()).isNull();
            tracing.deliver(receipt.commandId(), () -> {
                assertThat(tracer.currentSpan().context().traceId()).isEqualTo(parent.context().traceId());
                assertThat(tracer.currentSpan().context().spanId()).isNotEqualTo(parent.context().spanId());
            });
            assertThat(tracer.currentSpan()).isNull();
            var jdbc = context.getBean(JdbcTemplate.class);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_runtime_binding", String.class);
            assertThatThrownBy(() -> tracing.project(user, binding, () -> {
                assertThat(tracer.currentSpan().context().traceId()).isEqualTo(parent.context().traceId());
                throw new IllegalStateException("test rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(tracer.currentSpan()).isNull();
            String scopeId = jdbc.queryForObject("SELECT scope_id FROM sq_response_execution", String.class);
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding", String.class);
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            var scheduler = reactor.core.scheduler.Schedulers.fromExecutorService(executor);
            try {
                assertThat(tracing.session(user, session, "test.model", () -> reactor.core.publisher.Mono
                                .fromCallable(() -> tracer.currentSpan().context().traceId()).subscribeOn(scheduler).flux())
                        .blockLast(java.time.Duration.ofSeconds(5))).isEqualTo(parent.context().traceId());
                assertThat(tracer.currentSpan()).isNull();
                assertThat(executor.submit(tracer::currentSpan).get(5, java.util.concurrent.TimeUnit.SECONDS)).isNull();
                assertThatThrownBy(() -> tracing.session(user, session, "test.tool", () -> reactor.core.publisher.Mono
                                .error(new IllegalStateException("test tool failure")).subscribeOn(scheduler).flux())
                        .blockLast(java.time.Duration.ofSeconds(5))).isInstanceOf(IllegalStateException.class);
                assertThat(executor.submit(tracer::currentSpan).get(5, java.util.concurrent.TimeUnit.SECONDS)).isNull();
                var subscribed = new java.util.concurrent.CountDownLatch(1);
                var waiting = tracing.session(user, session, "test.cancel", () -> reactor.core.publisher.Mono
                        .fromRunnable(subscribed::countDown).subscribeOn(scheduler)
                        .thenMany(reactor.core.publisher.Flux.never())).subscribe();
                try {
                    assertThat(subscribed.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                } finally {
                    waiting.dispose();
                }
                assertThat(executor.submit(tracer::currentSpan).get(5, java.util.concurrent.TimeUnit.SECONDS)).isNull();
            } finally {
                scheduler.dispose();
            }
            var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
            var sessions = context.getBean(com.agentsquad.squad.agent.session.RuntimeSessionProvider.class);
            var source = new com.agentsquad.squad.bo.RuntimeEventSourceBO(user, binding, session, 1);
            String agent = jdbc.queryForObject("SELECT agent_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
            int journalObjects = jdbc.queryForObject("SELECT COUNT(*) FROM agentscope_store", Integer.class);
            assertThat(sessions.executionContext(source).getSessionId()).isEqualTo(session);
            assertThat(sessions.readCommitted(source, agent, 0, 100)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agentscope_store", Integer.class)).isEqualTo(journalObjects);
            assertThatThrownBy(() -> sessions.readCommitted(source, "another-agent", 0, 100)).isInstanceOf(IllegalStateException.class);
            var target = new ProjectionTargetBO(user, null, binding, "master", session, 0);
            var service = context.getBean(ActivityProjectionService.class);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var queued = new NativeEventViewBO(1, "queued", "tool/requested", "run", "turn", null, 1000,
                    Map.of("call", Map.of("id", "read-one", "name", "read_attachment", "input", Map.of("secret", "private-argument"))));
            transaction.executeWithoutResult(status -> service.accept(scope, target, queued));
            assertThat(jdbc.queryForObject("SELECT status FROM sq_execution_activity", String.class)).isEqualTo("QUEUED");
            assertThat(jdbc.queryForObject("SELECT started_at FROM sq_execution_activity", java.sql.Timestamp.class)).isNull();
            transaction.executeWithoutResult(status -> service.accept(scope, target, new NativeEventViewBO(2, "dispatch", "tool/dispatch", "run", "turn", null, 1100,
                    Map.of("toolCallId", "read-one", "name", "read_attachment"))));
            assertThat(jdbc.queryForObject("SELECT status FROM sq_execution_activity", String.class)).isEqualTo("RUNNING");
            var failed = new NativeEventViewBO(3, "failed", "tool/result", "run", "turn", null, 1200,
                    Map.of("message", Map.of("content", List.of(Map.of("type", "tool_result", "id", "read-one", "name", "read_attachment", "state", "error", "output", "private-result")))));
            transaction.executeWithoutResult(status -> service.accept(scope, target, failed));
            transaction.executeWithoutResult(status -> service.accept(scope, target, failed));
            transaction.executeWithoutResult(status -> service.accept(scope, target, queued));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_execution_activity", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_execution_activity", String.class)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT version FROM sq_execution_activity", Long.class)).isEqualTo(3);
            transaction.executeWithoutResult(status -> service.accept(scope, target, new NativeEventViewBO(4, "model-one", "model/dispatch", "run", "turn", null, 1300, Map.of("modelCallId", "one"))));
            transaction.executeWithoutResult(status -> service.accept(scope, target, new NativeEventViewBO(5, "model-two", "model/dispatch", "run", "turn", null, 1400, Map.of("modelCallId", "two"))));
            transaction.executeWithoutResult(status -> service.accept(scope, target, new NativeEventViewBO(6, "model-end", "model/end", "run", "turn", null, 1500, Map.of("modelCallId", "one", "status", "completed"))));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_execution_activity WHERE status='RUNNING'", Integer.class)).isEqualTo(1);
            transaction.executeWithoutResult(status -> service.accept(scope, target, new NativeEventViewBO(7, "chunk", "model/chunk", "run", "turn", null, 1600, Map.of("chunk", "private-reasoning"))));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_execution_activity", Integer.class)).isEqualTo(3);
            transaction.executeWithoutResult(status -> service.accept(scope, target, new NativeEventViewBO(8, "approval", "tool/suspended", "other-run", "other-turn", null, 1700,
                    Map.of("toolCallId", "ticket", "name", "mock_ticket_create"))));
            assertThat(jdbc.queryForObject("SELECT wait_reason FROM sq_execution_activity WHERE activity_type='EXTERNAL_ACTION'", String.class)).isEqualTo("APPROVAL");
            transaction.executeWithoutResult(status -> {
                service.accept(scope, target, new NativeEventViewBO(9, "rollback", "model/dispatch", "other-run", "other-turn", null, 1800, Map.of("modelCallId", "rollback")));
                status.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_execution_activity", Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event WHERE producer_key LIKE '%rollback%'", Integer.class)).isZero();
            var reads = context.getBean(ConversationQueryService.class);
            var snapshot = reads.snapshot(user, receipt.conversationId(), null, 40);
            assertThat(snapshot.activities()).hasSize(4).allMatch(a -> receipt.messageId().equals(a.sourceMessageId()));
            String published = new ObjectMapper().writeValueAsString(snapshot.activities());
            assertThat(published).contains("FILE_READ", "FAILED", "MODEL_CALL").doesNotContain("private-");
            assertThat(jdbc.queryForList("SELECT payload_json FROM sq_conversation_event WHERE event_type='activity.updated'", String.class))
                    .allMatch(payload -> !payload.contains("private-"));
            assertThatThrownBy(() -> reads.snapshot("00000000-0000-0000-0000-000000000002", receipt.conversationId(), null, 40))
                    .isInstanceOf(com.agentsquad.squad.exceptions.RuntimeBusinessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_execution_activity", Integer.class)).isEqualTo(4);
            transaction.executeWithoutResult(status -> {
                for (int i = 10; i <= 50; i++) {
                    service.accept(scope, target, new NativeEventViewBO(i, "history-" + i, "model/dispatch",
                            "run", "turn", null, 2000 + i, Map.of("modelCallId", "history-" + i)));
                }
            });
            var first = reads.activityHistory(user, receipt.conversationId(), new ActivityHistoryDTO(receipt.messageId(), null));
            assertThat(first.items()).hasSize(40);
            assertThat(first.nextCursor()).isEqualTo(first.items().getLast().activityId());
            var second = reads.activityHistory(user, receipt.conversationId(), new ActivityHistoryDTO(receipt.messageId(), first.nextCursor()));
            assertThat(second.items()).hasSize(5).extracting(ExecutionActivityVO::activityId)
                    .doesNotContainAnyElementsOf(first.items().stream().map(ExecutionActivityVO::activityId).toList());
            assertThat(second.nextCursor()).isNull();
            assertThat(reads.activityHistory(user, receipt.conversationId(), new ActivityHistoryDTO("other-message", first.nextCursor())).items()).isEmpty();
            assertThatThrownBy(() -> reads.activityHistory("00000000-0000-0000-0000-000000000002", receipt.conversationId(),
                    new ActivityHistoryDTO(receipt.messageId(), null)))
                    .isInstanceOf(com.agentsquad.squad.exceptions.RuntimeBusinessException.class);
        }
    }
}
