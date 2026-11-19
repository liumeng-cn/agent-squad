package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.converter.ChatSessionConverter;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.enums.ChatDisposition;
import com.agentsquad.squad.v2.enums.ChatExecutionStatus;
import com.agentsquad.squad.v2.enums.ChatMessageRole;
import com.agentsquad.squad.v2.service.ChatService;
import com.agentsquad.squad.v2.service.ChatRuntimeService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
            var nativeSession = agent.session(RuntimeContext.builder().userId("1").sessionId("conversation").build());
            assertThat(nativeSession.await(nativeSession.task(first.getInputId())).block(Duration.ofSeconds(20)).status())
                    .isEqualTo("completed");
            service.send(input, "1");
            assertThat(model.inputs).hasSize(1);
            var snapshot = sessions.read("conversation", "1");
            assertThat(snapshot.getStatus()).isEqualTo(ChatExecutionStatus.COMPLETED);
            assertThat(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(snapshot))
                    .doesNotContain("不可公开的测试提示词", "stateJson", "payloadJson");
            assertThat(snapshot.getMessages()).hasSize(2);
            assertThat(snapshot.getMessages().getFirst().getRole()).isEqualTo(ChatMessageRole.USER);
            assertThat(snapshot.getMessages().getLast().getContent()).isEqualTo("你好，世界");
            assertThat(snapshot.getMessages().getLast().getFinishedAt())
                    .isGreaterThanOrEqualTo(snapshot.getMessages().getLast().getStartedAt());
            assertThat(sessions.read("conversation", "1").getMessages().getLast().getFinishedAt())
                    .isEqualTo(snapshot.getMessages().getLast().getFinishedAt());
            assertThat(sessions.read("conversation", "2").getMessages()).isEmpty();

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
     *
     * 使用本地 PG 和脚本模型，跨工作区恢复历史，避免本地文件掩盖存储路由错误。
     */
    @Test
    @EnabledIfSystemProperty(named = "agentsquad.test.postgres", matches = "true")
    void restoresPostgresJournalWithoutLocalWorkspace() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-local.yml"));
        var configuration = yaml.getObject();
        String userId = "pg-storage-probe-" + UUID.randomUUID();
        Path firstWorkspace = workspace.resolve("first");
        Path restoredWorkspace = workspace.resolve("restored");

        try (var dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(configuration.getProperty("spring.datasource.url"));
            dataSource.setUsername(configuration.getProperty("spring.datasource.username"));
            dataSource.setPassword(configuration.getProperty("spring.datasource.password"));
            var store = JdbcDistributedStore.create(dataSource);
            try (var agent = HarnessAgent.builder().name("pg-probe").agentId("pg-probe")
                    .model(new ScriptedChatModel()).disableMemoryHooks().workspace(firstWorkspace)
                    .distributedStore(store)
                    .filesystem(new RemoteFilesystemSpec().addSharedPrefix("agents/")).build()) {
                var receipt = service(sessions(agent)).send(input("pg-first", "存储验证"), userId);
                var session = agent.session(RuntimeContext.builder()
                        .userId(userId).sessionId("conversation").build());
                assertThat(session.await(session.task(receipt.getInputId()))
                        .block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
                assertThat(store.baseStore().search(java.util.List.of(
                                "agents", "pg-probe", "users", userId, "agents",
                                "__agentscope_session_log_v1__"), 10000, 0))
                        .anyMatch(item -> item.key().contains("commits/"));
            }

            try (var paths = Files.walk(firstWorkspace)) {
                assertThat(paths.filter(path -> path.toString().contains(".agentscope-runtime"))
                        .filter(Files::isRegularFile).toList()).isEmpty();
            }
            try (var restored = HarnessAgent.builder().name("pg-probe").agentId("pg-probe")
                    .model(new ScriptedChatModel()).disableMemoryHooks().workspace(restoredWorkspace)
                    .distributedStore(JdbcDistributedStore.create(dataSource))
                    .filesystem(new RemoteFilesystemSpec().addSharedPrefix("agents/")).build()) {
                assertThat(sessions(restored).read("conversation", userId).getMessages()).hasSize(2);
                assertThat(sessions(restored).read("conversation", userId + "-other").getMessages()).isEmpty();
            }
        }
    }

    static ChatRuntimeService sessions(HarnessAgent agent) {
        var sessions = new ChatRuntimeService();
        ReflectionTestUtils.setField(sessions, "agent", agent);
        ReflectionTestUtils.setField(sessions, "converter", new ChatSessionConverter());
        return sessions;
    }

    static ChatService service(ChatRuntimeService sessions) {
        var service = new ChatService();
        ReflectionTestUtils.setField(service, "sessionService", sessions);
        return service;
    }

    static ChatMessageDTO input(String id, String text) {
        var input = new ChatMessageDTO();
        input.setSessionId("conversation");
        input.setInteractionId(id);
        input.setMessage(text);
        return input;
    }
}
