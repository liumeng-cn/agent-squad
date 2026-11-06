package com.agentsquad.experiments;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.session.InMemorySessionLogStore;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.session.AgentSession;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 原生回合和终结工具契约实验
 * <p>
 * 使用真实 Harness、Session 和日志协议验证模型调用次数与完成原因；不连接业务数据库。
 */
class SdkExecutionContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"stop", "length", "content_filter"})
    @NullAndEmptySource
    void inspectNaturalOutput(String reason, @TempDir Path directory) throws Exception {
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.text(reason)), false);
             var agent = agent(model, directory, new Toolkit(), 3)) {
            AgentSession session = session(agent);
            AgentSession.Task receipt = session.submit("natural-probe", "本地测试");
            AgentSession.Task settled = session.await(receipt).block(Duration.ofSeconds(15));
            List<SessionEvent> events = session.log().readAfter(0, 1000);
            assertThat(settled).isNotNull();
            assertThat(model.calls()).isEqualTo(1);
            assertThat(events).anyMatch(event -> event.type().equals("turn/output"));
            SessionEvent end = events.stream().filter(event -> event.type().equals("model/end")).findFirst().orElseThrow();
            assertThat(end.data().get("finishReason")).isEqualTo(reason);
            // 此断言刻意记录原生事实，不把 SDK completed 当成产品成功。
            assertThat(settled.status()).isEqualTo("completed");
        }
    }

    @Test
    void directSuccessSkipsSecondModelCall(@TempDir Path directory) throws Exception {
        verifyTool(directory, true, false, false, 1);
    }

    @Test
    void defaultToolRequiresSecondModelCall(@TempDir Path directory) throws Exception {
        verifyTool(directory, false, false, false, 2);
    }

    @Test
    void errorDoesNotShortCircuit(@TempDir Path directory) throws Exception {
        verifyTool(directory, true, true, false, 2);
    }

    @Test
    void mixedBatchDoesNotShortCircuit(@TempDir Path directory) throws Exception {
        verifyTool(directory, true, false, true, 2);
    }

    @Test
    void malformedModelResponseCannotBecomeSuccessfulRun(@TempDir Path directory) throws Exception {
        try (var model = new ScriptedModelServer(List.of("not-json"), false);
             var agent = agent(model, directory, new Toolkit(), 2)) {
            var session = session(agent);
            var receipt = session.submit("malformed-probe", "本地异常实验");
            var settled = session.await(receipt).block(Duration.ofSeconds(15));
            assertThat(settled).isNotNull();
            assertThat(settled.status()).isEqualTo("failed");
            assertThat(model.calls()).isEqualTo(1);
        }
    }

    @Test
    void iterationLimitCannotBecomeNormalAnswer(@TempDir Path directory) throws Exception {
        var finish = new ProbeTool("finish", false, false);
        Toolkit tools = new Toolkit();
        tools.registerAgentTool(finish);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(false), ScriptedModelServer.text("stop")), false);
             var agent = agent(model, directory, tools, 1)) {
            var session = session(agent);
            var receipt = session.submit("limit-probe", "本地迭代上限实验");
            var settled = session.await(receipt).block(Duration.ofSeconds(15));
            assertThat(settled).isNotNull();
            assertThat(settled.status()).isEqualTo("failed");
            var end = session.log().readAfter(0, 1000).stream()
                    .filter(event -> event.type().equals("run/end")).findFirst().orElseThrow();
            assertThat(end.payloadJson()).contains("MAX_ITERATIONS");
        }
    }

    private void verifyTool(Path directory, boolean direct, boolean error, boolean mixed, int expectedCalls) throws Exception {
        var finish = new ProbeTool("finish", direct, error);
        var lookup = new ProbeTool("lookup", false, false);
        Toolkit tools = new Toolkit();
        tools.registerAgentTool(finish);
        tools.registerAgentTool(lookup);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(mixed), ScriptedModelServer.text("stop")), false);
             var agent = agent(model, directory, tools, 3)) {
            AgentSession session = session(agent);
            var receipt = session.submit("tool-probe", "执行实验工具");
            var settled = session.await(receipt).block(Duration.ofSeconds(15));
            assertThat(settled).isNotNull();
            assertThat(settled.status()).isEqualTo("completed");
            assertThat(model.calls()).isEqualTo(expectedCalls);
            assertThat(finish.calls()).isEqualTo(1);
            assertThat(lookup.calls()).isEqualTo(mixed ? 1 : 0);
            var output = session.log().readAfter(0, 1000).stream()
                    .filter(event -> event.type().equals("turn/output")).findFirst().orElseThrow();
            if (expectedCalls == 1) {
                assertThat(output.payloadJson()).contains("实验工具结果").contains("TOOL_RETURN_DIRECT");
            }
        }
    }

    static AgentSession session(HarnessAgent agent) {
        return agent.session(RuntimeContext.builder().userId("probe-user")
                .sessionId(UUID.randomUUID().toString()).build());
    }

    static HarnessAgent agent(ScriptedModelServer model, Path workspace, Toolkit tools, int maxIters) {
        return agent(model, workspace, tools, maxIters, false);
    }

    static HarnessAgent agent(ScriptedModelServer model, Path workspace, Toolkit tools, int maxIters, boolean streaming) {
        return agent(model, workspace, tools, maxIters, streaming, new InMemorySessionLogStore());
    }

    static HarnessAgent agent(ScriptedModelServer model, Path workspace, Toolkit tools, int maxIters,
                              boolean streaming, io.agentscope.core.session.SessionLogStore store) {
        return HarnessAgent.builder().name("契约实验").agentId("probe-agent")
                .workspace(workspace).sessionLogStore(store)
                .model(OpenAIChatModel.builder().apiKey("local-probe-only").baseUrl(model.baseUrl())
                        .modelName("probe").stream(streaming).build())
                .modelExecutionConfig(ExecutionConfig.builder().timeout(Duration.ofSeconds(5)).maxAttempts(1).build())
                .toolkit(tools).maxIters(maxIters)
                .disableSubagents().disableFilesystemTools().disableShellTool().disableWebTools()
                .disableMemoryTools().disableMemoryHooks().disableDefaultWorkspaceSkills()
                .disableWorkspaceContext().disableToolsConfig().disableCompaction().build();
    }
}
