package com.agentsquad.squad.v2;

import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import reactor.core.publisher.Flux;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.message.TextBlock;

import java.util.List;

import io.agentscope.core.agent.RuntimeContext;

import java.time.Duration;

import io.agentscope.core.event.AgentEndEvent;

import com.agentsquad.squad.v2.agent.subagent.TestSubagentDefinitions;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 三种子 Agent 来源验证
 * <p>
 * 验证 SDK 合并内置、文件和编程式定义，并确认启动不会覆盖工作区内容。
 */
class TestSubagentDefinitionsTest {
    @TempDir
    Path workspace;

    @Test
    void createsAndRunsAllThreeDefinitions() {
        Model model = mock(Model.class);
        Mockito.when(model.getModelName()).thenReturn("subagent-test");
        Mockito.when(model.stream(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(Flux.just(ChatResponse.builder()
                        .content(List.of(TextBlock.builder().text("测试完成").build()))
                        .finishReason("stop").build()));

        var builder = HarnessAgent.builder().name("test").model(model).workspace(workspace);
        TestSubagentDefinitions.configure(builder, workspace);
        var context = RuntimeContext.builder().userId("test").sessionId("test").build();

        for (var entry : builder.buildSubagentEntries(workspace)) {
            try (var child = (HarnessAgent) entry.factory().create(context)) {
                var events = child.streamEvents("返回测试结果", context).collectList().block(Duration.ofSeconds(20));
                assertThat(events).as(entry.name()).isNotEmpty();
                assertThat(events).anyMatch(event -> event instanceof AgentEndEvent);
            }
        }
    }

    @Test
    void mergesThreeSourcesAndPreservesWorkspaceEdits() throws Exception {
        var builder = HarnessAgent.builder().name("test").model(mock(Model.class)).workspace(workspace);
        TestSubagentDefinitions.configure(builder, workspace);
        var entries = builder.buildSubagentEntries(workspace);
        assertThat(entries).extracting("name").contains("general-purpose", "markdown-tester", "java-tester");

        Path markdown = workspace.resolve("subagents/markdown-tester.md");
        Files.writeString(markdown, "用户修改");
        TestSubagentDefinitions.configure(HarnessAgent.builder(), workspace);
        assertThat(Files.readString(markdown)).isEqualTo("用户修改");
    }
}
