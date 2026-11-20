package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.dto.PageDTO;
import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.observer.AgentTracingMiddleware;
import com.agentsquad.squad.v2.converter.ChatConvConverter;
import com.agentsquad.squad.v2.converter.ChatSessionConverter;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.dto.ConvRenameDTO;
import com.agentsquad.squad.v2.enums.ChatDisposition;
import com.agentsquad.squad.v2.enums.ChatExecutionStatus;
import com.agentsquad.squad.v2.enums.ChatMessageRole;
import com.agentsquad.squad.v2.po.ChatConvPO;
import com.agentsquad.squad.v2.service.ChatConvDirectoryService;
import com.agentsquad.squad.v2.service.ChatConvService;
import com.agentsquad.squad.v2.service.ChatRuntimeService;
import com.agentsquad.squad.v2.service.ChatService;
import com.zaxxer.hikari.HikariDataSource;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionInbox;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 原生会话提交和历史验证
 * <p>
 * 使用真实 Harness 执行、幂等提交及纯读公开投影，验证订阅之外的会话行为。
 */
class AguiSessionTest {
    @TempDir
    Path workspace;

    @Test
    void submitsIdempotentlyAndRestoresHistory() {
        var model = new ScriptedChatModel();
        try (var agent = HarnessAgent.builder().name("test").agentId("test")
                .sysPrompt("不可公开的测试提示词").model(model).disableMemoryHooks().workspace(workspace).build()) {
            var sessions = sessions(agent);
            var service = service(sessions);
            var input = input("first", "问题一");
            var first = service.send(input, "1");
            var nativeSession = agent.session(RuntimeContext.builder().userId("1").sessionId("native-conversation").build());
            assertThat(nativeSession.await(nativeSession.task(first.getInputId())).block(Duration.ofSeconds(20)).status())
                    .isEqualTo("completed");
            service.send(input, "1");
            assertThat(model.inputs).hasSize(1);
            var snapshot = sessions.read("native-conversation", "1");
            assertThat(snapshot.getStatus()).isEqualTo(ChatExecutionStatus.COMPLETED);
            assertThat(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(snapshot))
                    .doesNotContain("不可公开的测试提示词", "stateJson", "payloadJson");
            assertThat(snapshot.getMessages()).hasSize(2);
            assertThat(snapshot.getMessages().getFirst().getRole()).isEqualTo(ChatMessageRole.USER);
            assertThat(snapshot.getMessages().getLast().getContent()).isEqualTo("你好，世界");
            assertThat(snapshot.getMessages().getLast().getFinishedAt())
                    .isGreaterThanOrEqualTo(snapshot.getMessages().getLast().getStartedAt());
            assertThat(sessions.read("native-conversation", "1").getMessages().getLast().getFinishedAt())
                    .isEqualTo(snapshot.getMessages().getLast().getFinishedAt());
            assertThat(sessions.read("native-conversation", "2").getMessages()).isEmpty();

            var next = input("second", "问题二");
            next.setObservedTurnId(first.getTurnId());
            var receipt = service.send(next, "1");
            assertThat(receipt.getDisposition()).isEqualTo(ChatDisposition.NEW_TURN);
            assertThat(receipt.getTurnId()).isNotEqualTo(first.getTurnId());
            assertThat(nativeSession.await(nativeSession.task(receipt.getInputId())).block(Duration.ofSeconds(20)).status())
                    .isEqualTo("completed");
        }
    }

    /**
     * 验证日志入库
     * <p>
     * 使用本地 PG 和脚本模型，跨工作区恢复历史，避免本地文件掩盖存储路由错误。
     */
    @Test
    @EnabledIfSystemProperty(named = "agentsquad.test.postgres", matches = "true")
    void restoresPostgresJournalWithoutLocalWorkspace() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-local.yml"));
        var configuration = yaml.getObject();
        String userId = "8401";
        Path firstWorkspace = workspace.resolve("first");
        Path restoredWorkspace = workspace.resolve("restored");

        try (var dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(System.getProperty("agentsquad.test.postgres.url", configuration.getProperty("spring.datasource.url")));
            dataSource.setUsername(System.getProperty("agentsquad.test.postgres.username", configuration.getProperty("spring.datasource.username")));
            dataSource.setPassword(System.getProperty("agentsquad.test.postgres.password", configuration.getProperty("spring.datasource.password")));
            var dialect = AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build();
            var store = JdbcDistributedStore.create(dataSource, dialect);
            String namespace = UUID.randomUUID().toString();
            var filesystem = new RemoteFilesystem(store.baseStore(), java.util.List.of("test", namespace));
            var journal = new WorkspaceSessionLogStore(filesystem);
            try (var agent = HarnessAgent.builder().name("pg-probe").agentId("pg-probe")
                    .model(new ScriptedChatModel()).disableMemoryHooks().workspace(firstWorkspace)
                    .abstractFilesystem(filesystem).sessionLogStore(journal).build()) {
                var receipt = service(sessions(agent)).send(input("pg-first", "存储验证"), userId);
                var session = agent.session(RuntimeContext.builder()
                        .userId(userId).sessionId("native-conversation").build());
                assertThat(session.await(session.task(receipt.getInputId()))
                        .block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
                assertThat(store.baseStore().search(java.util.List.of(
                        "test", namespace, "__agentscope_session_log_v1__"), 10000, 0))
                        .anyMatch(item -> item.key().contains("commits/"));
            }

            try (var paths = Files.walk(workspace)) {
                assertThat(paths.filter(path -> path.toString().contains(".agentscope-runtime"))
                        .filter(Files::isRegularFile).toList()).isEmpty();
            }
            var restoredModel = new ScriptedChatModel();
            try (var restored = HarnessAgent.builder().name("pg-probe").agentId("pg-probe")
                    .model(restoredModel).disableMemoryHooks().workspace(restoredWorkspace)
                    .abstractFilesystem(filesystem).sessionLogStore(journal).build()) {
                var runtime = sessions(restored);
                var snapshot = runtime.read("native-conversation", userId);
                assertThat(snapshot.getMessages()).hasSize(2);
                assertThat(runtime.read("native-conversation", "8402").getMessages()).isEmpty();

                var secondRead = runtime.read("native-conversation", userId);
                assertThat(secondRead.getCursor()).isEqualTo(snapshot.getCursor());
                assertThat(secondRead.getInputCursor()).isEqualTo(snapshot.getInputCursor());
                assertThat(restoredModel.inputs).isEmpty();

                var receipt = service(runtime).send(input("pg-next", "后续问题"), userId);
                var session = runtime.open("native-conversation", userId);
                assertThat(session.await(session.task(receipt.getInputId()))
                        .block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
                assertThat(restoredModel.inputs).hasSize(1);
                assertThat(restoredModel.inputs.getFirst())
                        .anyMatch(message -> message.getTextContent().contains("存储验证"))
                        .anyMatch(message -> message.getTextContent().contains("后续问题"));
                verifyReadOnlyRecoveryStates(runtime, restoredModel, userId);
            }
        }
    }

    private void verifyReadOnlyRecoveryStates(ChatRuntimeService runtime, ScriptedChatModel model, String userId) {
        String queuedId = "native-queued";
        var queued = runtime.open(queuedId, userId);
        var message = Msg.builder().textContent("已接收输入").role(MsgRole.USER).build();
        var command = new SessionInbox.Command("queued-input", "submit", "queued-turn",
                List.of(message), List.of());
        new SessionInbox(queued.log()).locked(transaction -> transaction.accept(command));

        int calls = model.inputs.size();
        var queuedSnapshot = runtime.read(queuedId, userId);
        assertThat(queuedSnapshot.getStatus()).isEqualTo(ChatExecutionStatus.QUEUED);
        assertThat(queuedSnapshot.getAcceptedInputIds()).containsExactly("queued-input");
        assertThat(queued.log().head().seq()).isZero();

        String interruptedId = "native-interrupted";
        var interrupted = runtime.open(interruptedId, userId);
        var journal = interrupted.log();
        var writer = journal.acquire("old-run", Duration.ofMinutes(1));
        var start = new SessionEvent(1, "run-start", 1, System.currentTimeMillis(),
                "run/start", "old-run", "interrupted-turn", true, "{}");
        journal.commit(writer, "run-start", 0, List.of(start));
        journal.release(writer);

        var stoppedSnapshot = runtime.read(interruptedId, userId);
        assertThat(stoppedSnapshot.getStatus()).isEqualTo(ChatExecutionStatus.INTERRUPTED);
        assertThat(stoppedSnapshot.getCursor()).isEqualTo(1L);
        assertThat(journal.head().seq()).isEqualTo(1L);
        assertThat(model.inputs).hasSize(calls);
    }

    /**
     * 验证缺表时 SDK 只校验结构
     */
    @Test
    @EnabledIfSystemProperty(named = "agentsquad.test.postgres", matches = "true")
    void rejectsMissingSdkTablesWithoutCreatingThem() throws Exception {
        String jdbcUrl = System.getProperty("agentsquad.test.postgres.url");
        try (var dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(jdbcUrl.replace("currentSchema=squad_chat", "currentSchema=public"));
            dataSource.setUsername(System.getProperty("agentsquad.test.postgres.username"));
            dataSource.setPassword(System.getProperty("agentsquad.test.postgres.password"));
            assertThatThrownBy(() -> AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build())
                    .isInstanceOf(IllegalStateException.class);
            try (var conn = dataSource.getConnection();
                 var tables = conn.getMetaData().getTables(null, "public", "agentscope%", null)) {
                assertThat(tables.next()).isFalse();
            }
        }
    }

    /**
     * 验证真实目录读写
     * <p>
     * 使用专用数据库检查 Mapper 绑定、幂等建档、标题版本和原生会话归属。
     */
    @Test
    @EnabledIfSystemProperty(named = "agentsquad.test.postgres", matches = "true")
    void persistsConvDirectoryAndChecksSessionOwnership() throws Exception {
        var agent = mock(HarnessAgent.class);
        when(agent.getAgentId()).thenReturn("pg-directory-test");
        var tracing = mock(AgentTracingMiddleware.class);
        var application = new SpringApplicationBuilder(AgentSquadV2Application.class);
        application.initializers(context -> {
            context.getBeanFactory().registerSingleton("testChatAgent", agent);
            context.getBeanFactory().registerSingleton("testTracing", tracing);
        });

        try (var context = application.run(
                "--server.port=0",
                "--agentsquad.v2.agent.enabled=false",
                "--agentsquad.v2.agent.agent-id=pg-directory-test",
                "--agentsquad.v2.observation.enabled=false",
                "--spring.datasource.url=" + System.getProperty("agentsquad.test.postgres.url"),
                "--spring.datasource.username=" + System.getProperty("agentsquad.test.postgres.username"),
                "--spring.datasource.password=" + System.getProperty("agentsquad.test.postgres.password"))) {
            var directory = context.getBean(ChatConvService.class);
            var first = directory.create("8411");
            String convId = first.getConvId();
            var otherUser = directory.create("8412");
            assertThat(first.getSessionId()).isNotEqualTo(convId);
            assertThat(otherUser.getSessionId()).isNotEqualTo(first.getSessionId());
            assertThat(directory.requireSession(first.getSessionId(), "8411").getConvId()).isEqualTo(convId);
            assertThatThrownBy(
                    () -> directory.requireSession(first.getSessionId(), "8412"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(ApiCode.NOT_FOUND));

            var rename = new ConvRenameDTO();
            rename.setTitle("  已修改标题  ");
            rename.setVersion(0L);
            var changed = directory.rename(convId, "8411", rename);
            assertThat(changed.getTitle()).isEqualTo("已修改标题");
            assertThat(changed.getVersion()).isEqualTo(1L);
            assertThat(directory.require(convId, "8411").getTitle()).isEqualTo("已修改标题");
            assertThatThrownBy(() -> directory.rename(convId, "8411", rename))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(ApiCode.CONFLICT));
            var nativeAgent = HarnessAgent.builder().name("pg-directory-test").agentId("pg-directory-test")
                    .model(new ScriptedChatModel()).workspace(workspace).build();
            try (nativeAgent) {
                when(agent.session(org.mockito.ArgumentMatchers.any(RuntimeContext.class)))
                        .thenAnswer(invocation -> nativeAgent.session(invocation.getArgument(0)));
                verifyDirectoryPagination(context.getBean(ChatConvDirectoryService.class), directory, convId);
            }
        }
    }

    private void verifyDirectoryPagination(ChatConvDirectoryService api, ChatConvService directory, String convId) {
        var newer = directory.create("8411");
        var page = api.list("8411", pageRequest(1, 1));
        assertThat(page.getRecords()).extracting("convId").containsExactly(newer.getConvId());
        assertThat(page.getTotal()).isEqualTo(2);
        var next = api.list("8411", pageRequest(2, 1));
        assertThat(next.getRecords()).extracting("convId").containsExactly(convId);
        assertThat(api.list("8412", pageRequest(1, 30)).getTotal()).isEqualTo(1);
    }

    private PageDTO pageRequest(long current, long size) {
        var request = new PageDTO();
        request.setCurrent(current);
        request.setSize(size);
        return request;
    }

    static ChatRuntimeService sessions(HarnessAgent agent) {
        var sessions = new ChatRuntimeService();
        ReflectionTestUtils.setField(sessions, "agent", agent);
        ReflectionTestUtils.setField(sessions, "converter", new ChatSessionConverter());
        var directory = mock(ChatConvService.class);
        when(directory.requireSession(anyString(), anyString())).thenAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            String userId = invocation.getArgument(1);
            var conversation = new ChatConvPO();
            conversation.setConvId("conversation");
            conversation.setSessionId(sessionId);
            conversation.setUserId(Long.valueOf(userId));
            conversation.setAgentId(agent.getAgentId());
            conversation.setCreateTime(java.time.Instant.EPOCH);
            return conversation;
        });
        ReflectionTestUtils.setField(sessions, "convs", directory);
        ReflectionTestUtils.setField(sessions, "conversationConverter",
                Mappers.getMapper(ChatConvConverter.class));
        return sessions;
    }

    static ChatService service(ChatRuntimeService sessions) {
        var service = new ChatService();
        ReflectionTestUtils.setField(service, "sessionService", sessions);
        return service;
    }

    static ChatMessageDTO input(String id, String text) {
        var input = new ChatMessageDTO();
        input.setConvId("conv");
        input.setSessionId("native-conversation");
        input.setInteractionId(id);
        input.setMessage(text);
        return input;
    }
}
