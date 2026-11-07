package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.domain.entity.DeliveryDO;
import com.agentsquad.squad.domain.request.MessageCreateRequest;
import com.agentsquad.squad.domain.response.MessageReceiptResponse;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.conversation.service.ResponseControlService;
import com.agentsquad.squad.integration.agentscope.service.CommandDeliveryService;
import com.agentsquad.squad.integration.agentscope.service.NativeProjectionService;
import com.agentsquad.squad.integration.agentscope.NativeEventView;
import com.agentsquad.squad.runtime.service.CompletionProposalService;
import com.agentsquad.squad.conversation.service.ResponseExecutionService;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.agentsquad.squad.integration.agentscope.NativeSessionGateway;
import com.agentsquad.squad.domain.model.MasterAgentIdentity;
import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.session.AgentSession;
import io.agentscope.core.agent.RuntimeContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无目标任务的正式SDK回复链路验证。
 * <p>
 * 使用持久消息入口、真实Bridge/SDK/journal和本地模型协议桩，覆盖追问、收据丢失与投影恢复。
 */
class ResponseNativeExecutionIntegrationTest {
    private static final String USER = "00000000-0000-0000-0000-000000000001";

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "\"{\""})
    void invalidSubmitPayloadIsBlockedThroughRealDelivery(String payload, @TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub();
             ConfigurableApplicationContext context = start(directory, model, false)) {
            MessageReceiptResponse receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "invalid",
                    new MessageCreateRequest("不会发给模型", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("UPDATE sq_command SET payload_json=?, due_at='2000-01-01' WHERE command_id=?",
                    payload, receipt.commandId());
            context.getBean(CommandDeliveryService.class).deliver(receipt.commandId());
            assertThat(jdbc.queryForObject("SELECT status FROM sq_command WHERE command_id=?",
                    String.class, receipt.commandId())).isEqualTo("BLOCKED");
            assertThat(jdbc.queryForObject("SELECT last_error_code FROM sq_command WHERE command_id=?",
                    String.class, receipt.commandId())).isEqualTo("INVALID_PAYLOAD");
            assertThat(model.calls.get()).isZero();
        }
    }

    @Test
    void platformDeletionIntentIsNotBlockedOrExecutedByNativePoller(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub();
             ConfigurableApplicationContext context = start(directory, model, false)) {
            MessageReceiptResponse receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "platform-delete",
                    new MessageCreateRequest("平台删除意图", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("UPDATE sq_command SET action='DELETE_CONVERSATION', due_at='2000-01-01' WHERE command_id=?",
                    receipt.commandId());
            CommandDeliveryService service = context.getBean(CommandDeliveryService.class);
            service.deliverDue();
            service.deliver(receipt.commandId());
            assertThat(jdbc.queryForObject("SELECT status FROM sq_command WHERE command_id=?",
                    String.class, receipt.commandId())).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT last_error_code FROM sq_command WHERE command_id=?",
                    String.class, receipt.commandId())).isNull();
            assertThat(model.calls.get()).isZero();
        }
    }

    @Test
    void invalidRespondPayloadIsBlockedBeforeCallingSdk(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub();
             ConfigurableApplicationContext context = start(directory, model, false)) {
            MessageReceiptResponse receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "invalid-respond",
                    new MessageCreateRequest("不会发给模型", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("UPDATE sq_command SET action='RESPOND', payload_json='{}', due_at='2000-01-01' WHERE command_id=?",
                    receipt.commandId());
            context.getBean(CommandDeliveryService.class).deliver(receipt.commandId());
            assertThat(jdbc.queryForObject("SELECT status FROM sq_command WHERE command_id=?",
                    String.class, receipt.commandId())).isEqualTo("BLOCKED");
            assertThat(model.calls.get()).isZero();
        }
    }

    @Test
    void unknownPendingActionIsBlockedWithoutExpandingExecutableWhitelist(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub();
             ConfigurableApplicationContext context = start(directory, model, false)) {
            MessageReceiptResponse receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "unknown-action",
                    new MessageCreateRequest("不会发给模型", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("UPDATE sq_command SET action='UNSUPPORTED', due_at='2000-01-01' WHERE command_id=?",
                    receipt.commandId());
            context.getBean(CommandDeliveryService.class).deliverDue();
            assertThat(jdbc.queryForObject("SELECT status FROM sq_command WHERE command_id=?",
                    String.class, receipt.commandId())).isEqualTo("BLOCKED");
            assertThat(jdbc.queryForObject("SELECT last_error_code FROM sq_command WHERE command_id=?",
                    String.class, receipt.commandId())).isEqualTo("UNKNOWN_ACTION");
            assertThat(model.calls.get()).isZero();
        }
    }

    @Test
    void concurrentSdkSessionsKeepUserMessagesAndCompletionToolsIsolated(@TempDir Path directory) throws Exception {
        String otherUser = "00000000-0000-0000-0000-000000000002";
        try (ResponseModelStub model = new ResponseModelStub(false, true);
             ConfigurableApplicationContext context = start(directory, model, true)) {
            MessageAcceptanceService messages = context.getBean(MessageAcceptanceService.class);
            MessageReceiptResponse first = messages.accept(USER, null, "same-idempotency",
                    new MessageCreateRequest("隔离标记甲：只处理我的请求", List.of()));
            MessageReceiptResponse second = messages.accept(otherUser, null, "same-idempotency",
                    new MessageCreateRequest("隔离标记乙：只处理我的请求", List.of()));
            assertThat(model.concurrentUsers.await(8, TimeUnit.SECONDS)).isTrue();
            assertThat(model.concurrencyTimedOut.get()).isFalse();
            await(context, first, "SUCCEEDED");
            await(context, second, "SUCCEEDED");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForList("SELECT content FROM sq_message WHERE user_id=? AND role='ASSISTANT'",
                    String.class, USER)).containsExactly("甲用户专属结果");
            assertThat(jdbc.queryForList("SELECT content FROM sq_message WHERE user_id=? AND role='ASSISTANT'",
                    String.class, otherUser)).containsExactly("乙用户专属结果");
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT framework_session_id) FROM sq_runtime_binding",
                    Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isZero();
            assertThat(model.calls.get()).isEqualTo(4);
            for (String request : model.requests) {
                boolean firstUser = request.contains("隔离标记甲");
                assertThat(request).contains(firstUser ? "隔离标记甲" : "隔离标记乙")
                        .doesNotContain(firstUser ? "隔离标记乙" : "隔离标记甲");
                if (request.contains("tool_call_id")) {
                    assertThat(request).contains(firstUser ? "甲用户专属结果" : "乙用户专属结果")
                            .doesNotContain(firstUser ? "乙用户专属结果" : "甲用户专属结果");
                }
            }
        }
    }

    @Test
    void answersFollowUpWithoutCreatingTasks(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub()) {
            MessageReceiptResponse first;
            try (ConfigurableApplicationContext beforeSubmit = start(directory, model, false)) {
                first = beforeSubmit.getBean(MessageAcceptanceService.class).accept(USER, null, "first",
                        new MessageCreateRequest("主角叫小明", List.of()));
                assertThat(model.calls.get()).isZero();
            }
            try (ConfigurableApplicationContext context = start(directory, model, true)) {
                MessageAcceptanceService messages = context.getBean(MessageAcceptanceService.class);
                await(context, first, "SUCCEEDED");
                MessageReceiptResponse next = messages.accept(USER, first.conversationId(), "follow", new MessageCreateRequest("他叫什么？", List.of()));
                await(context, next, "SUCCEEDED");
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForList("SELECT content FROM sq_message WHERE role='ASSISTANT' ORDER BY message_seq", String.class))
                        .containsExactly("记住了，小明。", "他叫小明。");
                assertThat(model.requests.get(2)).contains("主角叫小明", "记住了，小明", "他叫什么");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE status='SETTLED' AND task_id IS NULL", Long.class)).isEqualTo(4);
                assertThat(model.calls.get()).isEqualTo(4);
            }
            try (ConfigurableApplicationContext context = start(directory, model, true)) {
                Thread.sleep(300);
                assertThat(context.getBean(MessageAcceptanceService.class).accept(USER, null, "first", new MessageCreateRequest("主角叫小明", List.of())).executionId())
                        .isEqualTo(first.executionId());
                assertThat(model.calls.get()).isEqualTo(4);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lostReceiptReusesSdkTurnAndCompletedJournal(boolean saveReceipt, @TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub()) {
            MessageReceiptResponse receipt;
            String turn;
            try (ConfigurableApplicationContext context = start(directory, model, false)) {
                receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "lost", new MessageCreateRequest("主角叫小明", List.of()));
                context.getBean(JdbcTemplate.class).update("UPDATE sq_command SET due_at='2000-01-01' WHERE command_id=?", receipt.commandId());
                DeliveryMapper deliveries = context.getBean(DeliveryMapper.class);
                String token = UUID.randomUUID().toString();
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                assertThat(deliveries.claim(receipt.commandId(), token, now, now.plusSeconds(60))).isEqualTo(1);
                DeliveryDO delivery = deliveries.findClaimed(receipt.commandId(), token);
                NativeSessionGateway gateway = context.getBean(NativeSessionGateway.class);
                if (saveReceipt) {
                    turn = gateway.submit(delivery, "主角叫小明").turnId();
                } else {
                    // 模拟SDK已接受旧格式输入；接管时不能按新版上下文重编码同一提交键。
                    HarnessAgent previous = (HarnessAgent) ReflectionTestUtils.getField(gateway, "master");
                    turn = previous.session(RuntimeContext.builder().userId(USER).sessionId(delivery.sessionId()).build())
                            .submit(delivery.commandId(), "主角叫小明").turnId();
                }
                ProjectionTargetDO target = target(delivery);
                for (int i = 0; i < 150; i++) {
                    if ("completed".equals(gateway.inspectTask(target, receipt.commandId()).status())) {
                        break;
                    }
                    Thread.sleep(50);
                }
                assertThat(gateway.inspectTask(target, receipt.commandId()).status()).isEqualTo("completed");
                assertThat(model.calls.get()).isEqualTo(2);
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                if (saveReceipt) {
                    assertThat(deliveries.saveReceipt(receipt.commandId(), token, turn, null, "completed",
                            LocalDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
                } else {
                    assertThat(jdbc.queryForObject("SELECT native_turn_id FROM sq_command WHERE command_id=?", String.class, receipt.commandId())).isNull();
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isZero();
                jdbc.update("UPDATE sq_command SET delivery_lease_until='2000-01-01' WHERE command_id=?", receipt.commandId());
            }
            try (ConfigurableApplicationContext context = start(directory, model, true)) {
                await(context, receipt, "SUCCEEDED");
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("SELECT native_turn_id FROM sq_command WHERE command_id=?", String.class, receipt.commandId())).isEqualTo(turn);
                assertThat(model.calls.get()).isEqualTo(2);
                String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
                String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
                ProjectionTargetDO target = new ProjectionTargetDO(USER, null, binding, MasterAgentIdentity.AGENT_ID, session, 0);
                for (var event : context.getBean(NativeSessionGateway.class).readEvents(target, 0, 100)) {
                    context.getBean(NativeProjectionService.class).accept(target, event);
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isEqualTo(1);
                assertThat(model.calls.get()).isEqualTo(2);
            }
        }
    }

    @Test
    void stopBeforeDeliveryCancelsOnlyReply(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub(); ConfigurableApplicationContext context = start(directory, model, false)) {
            MessageReceiptResponse receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "stop", new MessageCreateRequest("请回答", List.of()));
            var command = context.getBean(ResponseControlService.class).cancel(USER, receipt.executionId(), "stop-key", 0);
            context.getBean(JdbcTemplate.class).update("UPDATE sq_command SET due_at='2000-01-01' WHERE command_id=?", command.commandId());
            context.getBean(CommandDeliveryService.class).deliver(command.commandId());
            await(context, receipt, "CANCELLED");
            assertThat(model.calls.get()).isZero();
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isZero();
        }
    }

    @Test
    void sealedWriterResumesSameTurnWithPersistedCandidate(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub(true);
             ConfigurableApplicationContext first = start(directory, model, false)) {
            MessageReceiptResponse receipt = first.getBean(MessageAcceptanceService.class).accept(USER, null, "crash",
                    new MessageCreateRequest("主角叫小明", List.of()));
            first.getBean(JdbcTemplate.class).update("UPDATE sq_command SET due_at='2000-01-01' WHERE command_id=?", receipt.commandId());
            first.getBean(CommandDeliveryService.class).deliver(receipt.commandId());
            assertThat(model.blocked.await(10, TimeUnit.SECONDS)).isTrue();
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            String sessionId = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
            String turn = jdbc.queryForObject("SELECT native_turn_id FROM sq_command WHERE command_id=?", String.class, receipt.commandId());
            assertThat(jdbc.queryForObject("SELECT completion_proposal_json FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId()))
                    .contains("小明");
            HarnessAgent master = (HarnessAgent) ReflectionTestUtils.getField(first.getBean(NativeSessionGateway.class), "master");
            AgentSession oldSession = master.session(RuntimeContext.builder().userId(USER).sessionId(sessionId).build());
            String writer = oldSession.log().head().owner();
            assertThat(writer).isNotBlank();
            oldSession.log().sealWriter(writer);
            try (ConfigurableApplicationContext peer = start(directory, model, true)) {
                await(peer, receipt, "SUCCEEDED");
                JdbcTemplate other = peer.getBean(JdbcTemplate.class);
                assertThat(other.queryForObject("SELECT COUNT(*) FROM sq_command WHERE binding_id=? AND action='MESSAGE_SUBMIT'", Long.class, binding)).isEqualTo(1);
                assertThat(other.queryForObject("SELECT COUNT(*) FROM sq_command WHERE binding_id=? AND action='RESUME' AND native_turn_id=?", Long.class, binding, turn)).isEqualTo(1);
                assertThat(other.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isEqualTo(1);
                assertThat(other.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isZero();
                assertThat(other.queryForObject("SELECT proposal_turn_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo(turn);
            } finally {
                model.release.countDown();
            }
        }
    }

    @Test
    void revokedGenerationCannotPublishRecordedCandidate(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub(); ConfigurableApplicationContext context = start(directory, model, false)) {
            MessageReceiptResponse receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "revoked", new MessageCreateRequest("请回答", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
            String turn = UUID.randomUUID().toString();
            ProjectionTargetDO target = new ProjectionTargetDO(USER, null, binding, MasterAgentIdentity.AGENT_ID, session, 0);
            NativeProjectionService projector = context.getBean(NativeProjectionService.class);
            projector.accept(target, new NativeEventView(1, "start", "run/start", "run", turn, null, 0, java.util.Map.of()));
            context.getBean(CompletionProposalService.class).propose(USER, session, turn, "proposal", "SUCCEEDED", "不应公开的旧候选");
            jdbc.update("UPDATE sq_conversation SET generation=generation+1 WHERE conversation_id=?", receipt.conversationId());
            projector.accept(target, new NativeEventView(2, "end", "run/end", "run", turn, "completed", 0, java.util.Map.of()));
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isZero();
            assertThat(model.calls.get()).isZero();
        }
    }

    @Test
    void unknownWritePreventsCompletionCorrectionAndPrematureCancellation(@TempDir Path directory) throws Exception {
        try (ResponseModelStub model = new ResponseModelStub(); ConfigurableApplicationContext context = start(directory, model, false)) {
            MessageReceiptResponse receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "unknown", new MessageCreateRequest("请处理", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
            String turn = UUID.randomUUID().toString();
            ProjectionTargetDO target = new ProjectionTargetDO(USER, null, binding, MasterAgentIdentity.AGENT_ID, session, 0);
            NativeProjectionService projector = context.getBean(NativeProjectionService.class);
            projector.accept(target, new NativeEventView(1, "start", "run/start", "run", turn, null, 0, java.util.Map.of()));
            String operation = UUID.randomUUID().toString();
            context.getBean(OperationMapper.class).insert(operation, USER, null, null, binding, turn, "write", "mock_ticket_create", "1.0.0",
                    "intent", "WRITE", UUID.randomUUID().toString(), 0, 0, "0".repeat(64), "{}", "UNKNOWN", "tester", "tool:intent");
            projector.accept(target, new NativeEventView(2, "end", "run/end", "run", turn, "completed", 0, java.util.Map.of()));
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo("RECONCILING");
            assertThat(jdbc.queryForObject("SELECT correction_count FROM sq_response_execution WHERE execution_id=?", Integer.class, receipt.executionId())).isZero();
            long version = jdbc.queryForObject("SELECT version FROM sq_response_execution WHERE execution_id=?", Long.class, receipt.executionId());
            context.getBean(ResponseControlService.class).cancel(USER, receipt.executionId(), "cancel", version);
            context.getBean(ResponseExecutionService.class).stopped(USER, binding);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo("RECONCILING");
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id=?", String.class, operation)).isEqualTo("UNKNOWN");
            // 此处仅模拟核对后已知的数据库事实，不冒充连接器核对端到端实验。
            jdbc.update("UPDATE sq_operation SET status='SUCCEEDED' WHERE operation_id=?", operation);
            context.getBean(ResponseExecutionService.class).stopped(USER, binding);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isZero();
            assertThat(model.calls.get()).isZero();
        }
    }

    @Test
    void replyStopWaitsForWriterReleaseWithoutStartingQueuedWork(@TempDir Path directory) throws Exception {
        try (var model = new ResponseModelStub(); var context = start(directory, model, false)) {
            var receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "stop-writer", new MessageCreateRequest("请回答", List.of()));
            var jdbc = context.getBean(JdbcTemplate.class);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
            var master = (io.agentscope.harness.agent.HarnessAgent) org.springframework.test.util.ReflectionTestUtils
                    .getField(context.getBean(com.agentsquad.squad.agent.MasterAgent.class), "master");
            var runtime = io.agentscope.core.agent.RuntimeContext.builder().userId(USER).sessionId(session).build();
            var store = new io.agentscope.harness.agent.session.WorkspaceSessionLogStore(master.getWorkspaceManager());
            var log = store.open(new io.agentscope.core.session.SessionKey(USER, MasterAgentIdentity.AGENT_ID, session), runtime);
            var writer = log.acquire("remote-owner", java.time.Duration.ofSeconds(30));
            var stop = context.getBean(ResponseControlService.class).cancel(USER, receipt.executionId(), "stop", 0);
            context.getBean(CommandDeliveryService.class).deliver(stop.commandId());
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo("CANCELLING");
            var scheduler = context.getBean(com.agentsquad.squad.runtime.service.CancellationSettlementScheduler.class);
            scheduler.settle();
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo("CANCELLING");
            log.release(writer);
            scheduler.settle();
            assertThat(jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId())).isEqualTo("CANCELLED");
            assertThat(model.calls.get()).isZero();
        }
    }

    @Test
    void stopsReplyWhileModelConnectionIsBlocked(@TempDir Path directory) throws Exception {
        try (var model = new ResponseModelStub(true); var context = start(directory, model, true)) {
            var receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "stop-active", new MessageCreateRequest("请回答", List.of()));
            assertThat(model.blocked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var jdbc = context.getBean(JdbcTemplate.class);
            long version = jdbc.queryForObject("SELECT version FROM sq_response_execution WHERE execution_id=?", Long.class, receipt.executionId());
            context.getBean(ResponseControlService.class).cancel(USER, receipt.executionId(), "stop-active", version);
            String status = null;
            for (int attempt = 0; attempt < 200; attempt++) {
                status = jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
                if ("CANCELLED".equals(status)) {
                    break;
                }
                Thread.sleep(100);
            }
            model.release.countDown();
            assertThat(status).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE execution_id=? AND role='ASSISTANT'", Long.class, receipt.executionId())).isZero();
            assertThat(model.calls.get()).isEqualTo(2);
        }
    }

    static ConfigurableApplicationContext start(Path directory, ResponseModelStub model, boolean fast) {
        return SpringApplication.run(AgentSquadApplication.class, "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("reply")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.response.completion-protocol=PROPOSAL",
                "--agentsquad.delivery.wakeup-enabled=false", "--agentsquad.projection.wakeup-enabled=false",
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=" + (fast ? 100 : 3600000),
                "--agentsquad.projection.poll-delay-ms=" + (fast ? 100 : 3600000), "--agentsquad.model.api-key=test-only",
                "--agentsquad.model.base-url=" + model.baseUrl(), "--agentsquad.model.name=test-model",
                "--agentsquad.worker.model-profiles.default.api-key=test-only",
                "--agentsquad.worker.model-profiles.default.base-url=" + model.baseUrl(),
                "--agentsquad.worker.model-profiles.default.model-name=test-model");
    }

    static void await(ConfigurableApplicationContext context, MessageReceiptResponse receipt, String expected) throws Exception {
        String status = null;
        for (int i = 0; i < 200; i++) {
            status = context.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            if (expected.equals(status) || "FAILED".equals(status)) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(status).isEqualTo(expected);
    }

    private ProjectionTargetDO target(DeliveryDO delivery) {
        return new ProjectionTargetDO(USER, null, delivery.bindingId(), delivery.agentId(), delivery.sessionId(), 0);
    }
}
