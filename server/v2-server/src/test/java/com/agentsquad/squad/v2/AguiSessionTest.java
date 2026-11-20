package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.vo.PageVO;
import com.agentsquad.squad.v2.agent.middleware.LangfuseEnrichmentMiddleware;
import com.agentsquad.squad.v2.converter.ChatConvConverter;
import com.agentsquad.squad.v2.converter.ChatSessionConverter;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.dto.ConvPageQueryDTO;
import com.agentsquad.squad.v2.enums.ChatDisposition;
import com.agentsquad.squad.v2.enums.ChatExecutionStatus;
import com.agentsquad.squad.v2.enums.ChatMessageRole;
import com.agentsquad.squad.v2.po.ChatConvPO;
import com.agentsquad.squad.v2.service.ChatConvService;
import com.agentsquad.squad.v2.service.ChatRuntimeService;
import com.agentsquad.squad.v2.service.ChatService;
import com.agentsquad.squad.v2.vo.ConvSummaryVO;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
    void derivesTitleFromFirstMessageWithoutOverwritingExistingTitle() {
        var mapper = mock(com.agentsquad.squad.v2.mapper.ChatConvMapper.class);
        var directory = new ChatConvService();
        ReflectionTestUtils.setField(directory, "mapper", mapper);
        ReflectionTestUtils.setField(directory, "properties",
                new com.agentsquad.squad.v2.config.properties.ChatAgentProperties());

        var first = asUser("1", () -> directory.register("title-test", "  分析\n 本周\t任务  "));
        assertThat(first.getTitle()).isEqualTo("分析 本周 任务");
        assertThat(first.getTitleSource()).isEqualTo(com.agentsquad.squad.v2.enums.ConvTitleSource.AUTO);

        var longTitle = asUser("1", () -> directory.register("long-title", "😀".repeat(41)));
        assertThat(longTitle.getTitle()).isEqualTo("😀".repeat(40));

        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(first);
        assertThat(asUser("1", () -> directory.register("title-test", "第二条发言")).getTitle())
                .isEqualTo("分析 本周 任务");

        first.setTitle("手动标题");
        first.setTitleSource(com.agentsquad.squad.v2.enums.ConvTitleSource.MANUAL);
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(first);
        var existing = asUser("1", () -> directory.register("title-test", "后续发言"));
        assertThat(existing.getTitle()).isEqualTo("手动标题");
        Mockito.verify(mapper, Mockito.times(2)).insert(org.mockito.ArgumentMatchers.any(ChatConvPO.class));
    }

    @Test
    void resolvesBoundDefinitionInsteadOfSdkInstanceId() {
        var agent = mock(HarnessAgent.class);
        var directory = new ChatConvService();
        ReflectionTestUtils.setField(directory, "chatAgent", agent);
        ReflectionTestUtils.setField(directory, "properties",
                new com.agentsquad.squad.v2.config.properties.ChatAgentProperties());
        var conversation = new ChatConvPO();
        conversation.setAgentDefinitionId("squad-master");

        assertThat(directory.resolveAgent(conversation)).isSameAs(agent);
        Mockito.verifyNoInteractions(agent);

        conversation.setAgentDefinitionId("another-agent");
        assertThatThrownBy(() -> directory.resolveAgent(conversation))
                .isInstanceOf(BusinessException.class)
                .hasMessage("对话绑定的 Agent 未注册");
    }

    @Test
    void submitsIdempotentlyAndRestoresHistory() {
        var model = new ScriptedChatModel();
        try (var agent = HarnessAgent.builder().name("test").agentId("test")
                .sysPrompt("不可公开的测试提示词").model(model).disableMemoryHooks().workspace(workspace).build()) {
            var runtime = runtime(agent);
            var service = service(runtime);
            var input = input("first", "问题一");
            var first = service.send(input);
            var nativeSession = agent.session(RuntimeContext.builder().userId("1").sessionId("native-conversation").build());
            assertThat(nativeSession.await(nativeSession.task(first.getInputId())).block(Duration.ofSeconds(20)).status())
                    .isEqualTo("completed");
            service.send(input);
            assertThat(model.inputs).hasSize(1);
            var snapshot = runtime.read("conversation");
            assertThat(snapshot.getStatus()).isEqualTo(ChatExecutionStatus.COMPLETED);
            assertThat(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(snapshot))
                    .doesNotContain("不可公开的测试提示词", "stateJson", "payloadJson");
            assertThat(snapshot.getMessages()).hasSize(2);
            assertThat(snapshot.getMessages().getFirst().getRole()).isEqualTo(ChatMessageRole.USER);
            assertThat(snapshot.getMessages().getLast().getContent()).isEqualTo("你好，世界");
            assertThat(snapshot.getMessages().getLast().getFinishedAt())
                    .isGreaterThanOrEqualTo(snapshot.getMessages().getLast().getStartedAt());
            assertThat(runtime.read("conversation").getMessages().getLast().getFinishedAt())
                    .isEqualTo(snapshot.getMessages().getLast().getFinishedAt());

            var next = input("second", "问题二");
            next.setObservedTurnId(first.getTurnId());
            var receipt = service.send(next);
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
                var runtime = runtime(agent, "8401");
                var receipt = service(runtime).send(input("pg-first", "存储验证"));
                var session = agent.session(RuntimeContext.builder()
                        .userId("8401").sessionId("native-conversation").build());
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
                var runtime = runtime(restored, "8401");
                var snapshot = runtime.read("conversation");
                assertThat(snapshot.getMessages()).hasSize(2);

                var secondRead = runtime.read("conversation");
                assertThat(secondRead.getCursor()).isEqualTo(snapshot.getCursor());
                assertThat(secondRead.getInputCursor()).isEqualTo(snapshot.getInputCursor());
                assertThat(restoredModel.inputs).isEmpty();

                var receipt = service(runtime).send(input("pg-next", "后续问题"));
                var session = runtime.open("conversation");
                assertThat(session.await(session.task(receipt.getInputId()))
                        .block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
                assertThat(restoredModel.inputs).hasSize(1);
                assertThat(restoredModel.inputs.getFirst())
                        .anyMatch(message -> message.getTextContent().contains("存储验证"))
                        .anyMatch(message -> message.getTextContent().contains("后续问题"));
                verifyReadOnlyRecoveryStates(runtime, restoredModel);
            }
        }
    }

    private void verifyReadOnlyRecoveryStates(ChatRuntimeService runtime, ScriptedChatModel model) {
        var queuedRuntime = runtimeAt(runtime, "queued-conv", "native-queued");
        var queued = queuedRuntime.open("queued-conv");
        var message = Msg.builder().textContent("已接收输入").role(MsgRole.USER).build();
        var command = new SessionInbox.Command("queued-input", "submit", "queued-turn",
                List.of(message), List.of());
        new SessionInbox(queued.log()).locked(transaction -> transaction.accept(command));

        int calls = model.inputs.size();
        var queuedSnapshot = queuedRuntime.read("queued-conv");
        assertThat(queuedSnapshot.getStatus()).isEqualTo(ChatExecutionStatus.QUEUED);
        assertThat(queuedSnapshot.getAcceptedInputIds()).containsExactly("queued-input");
        assertThat(queued.log().head().seq()).isZero();

        var interruptedRuntime = runtimeAt(runtime, "interrupted-conv", "native-interrupted");
        var interrupted = interruptedRuntime.open("interrupted-conv");
        var journal = interrupted.log();
        var writer = journal.acquire("old-run", Duration.ofMinutes(1));
        var start = new SessionEvent(1, "run-start", 1, System.currentTimeMillis(),
                "run/start", "old-run", "interrupted-turn", true, "{}");
        journal.commit(writer, "run-start", 0, List.of(start));
        journal.release(writer);

        var stoppedSnapshot = interruptedRuntime.read("interrupted-conv");
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
     * 使用专用数据库检查 Mapper 绑定、幂等建档、标题版本和归档删除。
     */
    @Test
    @EnabledIfSystemProperty(named = "agentsquad.test.postgres", matches = "true")
    void persistsConvDirectoryAndChecksOwnership() throws Exception {
        var agent = mock(HarnessAgent.class);
        var tracing = mock(LangfuseEnrichmentMiddleware.class);
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
            var first = asUser("8411", () -> directory.register("conv-alpha", "分析首次发言"));
            assertThat(first.getConvId()).isEqualTo("conv-alpha");
            assertThat(first.getSessionId()).isNotBlank();

            // 同一标识重复建档返回原记录，不覆盖标题和原生会话
            var again = asUser("8411", () -> directory.register("conv-alpha", "分析首次发言"));
            assertThat(again.getSessionId()).isEqualTo(first.getSessionId());
            assertThat(again.getTitle()).isEqualTo("分析首次发言");

            // 不同用户可以使用相同对话标识
            var otherUser = asUser("8412", () -> directory.register("conv-alpha", "分析首次发言"));
            assertThat(otherUser.getSessionId()).isNotEqualTo(first.getSessionId());
            assertThatThrownBy(() -> asUser("8412", () -> directory.require("missing-conv")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(ApiCode.NOT_FOUND));

            // 改名按版本条件更新，旧版本被拒绝
            var renamed = asUser("8411", () -> directory.rename("conv-alpha", "  已修改标题  ", 0L));
            assertThat(renamed.getTitle()).isEqualTo("已修改标题");
            assertThat(renamed.getVersion()).isEqualTo(1L);
            assertThatThrownBy(() -> asUser("8411", () -> directory.rename("conv-alpha", "旧版本", 0L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(ApiCode.CONFLICT));

            // 归档后从主列表移除，可通过归档列表找回并恢复
            asUser("8411", () -> { directory.archive("conv-alpha"); return null; });
            assertThat(asUser("8411", () -> directory.page(pageRequest(1, 10, false)).getTotal())).isZero();
            assertThat(asUser("8411", () -> directory.page(pageRequest(1, 10, true)).getTotal())).isEqualTo(1);
            asUser("8411", () -> { directory.unarchive("conv-alpha"); return null; });
            PageVO<ConvSummaryVO> mainList = asUser("8411", () -> directory.page(pageRequest(1, 10, false)));
            assertThat(mainList.getTotal()).isEqualTo(1);

            // 删除后所有入口按不存在处理
            asUser("8411", () -> { directory.delete("conv-alpha"); return null; });
            assertThatThrownBy(() -> asUser("8411", () -> directory.read("conv-alpha")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(ApiCode.NOT_FOUND));
        }
    }

    private ConvPageQueryDTO pageRequest(long current, long size, boolean archived) {
        var request = new ConvPageQueryDTO();
        request.setCurrent(current);
        request.setSize(size);
        request.setArchived(archived);
        return request;
    }

    /**
     * 在指定用户身份下执行目录操作，操作结束恢复上下文
     */
    static <T> T asUser(String userId, java.util.function.Supplier<T> action) {
        try (MockedStatic<com.agentsquad.squad.common.utils.CurrentUserContext> context =
                     Mockito.mockStatic(com.agentsquad.squad.common.utils.CurrentUserContext.class)) {
            context.when(com.agentsquad.squad.common.utils.CurrentUserContext::getUserId).thenReturn(userId);
            return action.get();
        }
    }

    /**
     * 构造归属固定用户的目录服务，测试不需要数据库
     */
    static ChatRuntimeService runtime(HarnessAgent agent) {
        return runtime(agent, "1");
    }

    static ChatRuntimeService runtime(HarnessAgent agent, String userId) {
        var runtime = new ChatRuntimeService();
        ReflectionTestUtils.setField(runtime, "converter", new ChatSessionConverter());
        ReflectionTestUtils.setField(runtime, "convConverter", Mappers.getMapper(ChatConvConverter.class));
        ReflectionTestUtils.setField(runtime, "convs", directory(agent, userId, "conversation", "native-conversation"));
        return runtime;
    }

    /**
     * 在既有运行时上追加一个对话归属映射，用于同会话多目录的恢复验证
     */
    static ChatRuntimeService runtimeAt(ChatRuntimeService runtime, String convId, String sessionId) {
        var existing = (ChatConvService) ReflectionTestUtils.getField(runtime, "convs");
        var agent = existing.resolveAgent(null);
        var directory = mock(ChatConvService.class);
        when(directory.resolveAgent(org.mockito.ArgumentMatchers.any())).thenReturn(agent);
        when(directory.requireOwned(anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> directory.require(invocation.getArgument(0)));
        when(directory.require(anyString())).thenAnswer(invocation -> {
            String requested = invocation.getArgument(0);
            if (requested.equals(convId)) {
                return conv(agent, convId, sessionId);
            }
            return existing.require(requested);
        });
        ReflectionTestUtils.setField(runtime, "convs", directory);
        return runtime;
    }

    private static ChatConvService directory(HarnessAgent agent, String userId, String convId, String sessionId) {
        var directory = mock(ChatConvService.class);
        when(directory.resolveAgent(org.mockito.ArgumentMatchers.any())).thenReturn(agent);
        when(directory.requireOwned(anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> directory.require(invocation.getArgument(0)));
        when(directory.require(anyString())).thenAnswer(invocation -> conv(agent, invocation.getArgument(0), sessionId, userId));
        return directory;
    }

    private static ChatConvPO conv(HarnessAgent agent, String convId, String sessionId, String userId) {
        var conversation = new ChatConvPO();
        conversation.setConvId(convId);
        conversation.setSessionId(sessionId);
        conversation.setUserId(Long.valueOf(userId));
        conversation.setAgentDefinitionId("squad-master");
        conversation.setCreateTime(java.time.Instant.EPOCH);
        return conversation;
    }

    private static ChatConvPO conv(HarnessAgent agent, String convId, String sessionId) {
        return conv(agent, convId, sessionId, "1");
    }

    static ChatService service(ChatRuntimeService runtime) {
        var service = new ChatService();
        ReflectionTestUtils.setField(service, "sessionService", runtime);
        var convs = mock(ChatConvService.class);
        when(convs.register(anyString(), anyString())).thenAnswer(invocation -> {
            var conversation = new ChatConvPO();
            conversation.setConvId(invocation.getArgument(0));
            conversation.setSessionId("native-conversation");
            conversation.setUserId(1L);
            conversation.setCreateTime(java.time.Instant.EPOCH);
            return conversation;
        });
        ReflectionTestUtils.setField(service, "convs", convs);
        return service;
    }

    static ChatMessageDTO input(String id, String text) {
        var input = new ChatMessageDTO();
        input.setConvId("conversation");
        input.setInteractionId(id);
        input.setMessage(text);
        return input;
    }
}
