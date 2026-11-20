package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.subagent.TestSubagentDefinitions;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 三种子 Agent 来源验证
 * <p>
 * 验证三个角色可执行，以及工作区按父用户和会话隔离。
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

        var entries = TestSubagentDefinitions.entries(model, new Toolkit(), workspace, new InMemoryStore());
        var context = RuntimeContext.builder().userId("test").sessionId("test").build();

        for (var entry : entries) {
            long cursor;
            try (var child = (HarnessAgent) entry.factory().create(context)) {
                var session = child.session(context);
                var task = session.submit("test-command", "返回测试结果");
                var completed = session.await(task).block(Duration.ofSeconds(20));
                assertThat(completed).as(entry.name()).isNotNull();
                assertThat(completed.status()).isEqualTo("completed");
                cursor = session.log().head().seq();
                assertThat(cursor).isPositive();
            }
            try (var restored = (HarnessAgent) entry.factory().create(context)) {
                assertThat(restored.session(context).log().head().seq()).isEqualTo(cursor);
            }
        }
    }

    @Test
    void isolatesRemoteWorkspaceByParentAndRole() {
        var entries = TestSubagentDefinitions.entries(mock(Model.class), new Toolkit(), workspace, new InMemoryStore());
        assertThat(entries).extracting("name").containsExactly("general-purpose", "markdown-tester", "java-tester");
        var parent = RuntimeContext.builder().userId("1").sessionId("one").build();
        var otherUser = RuntimeContext.builder().userId("2").sessionId("one").build();
        var otherSession = RuntimeContext.builder().userId("1").sessionId("two").build();
        var factory = entries.getFirst().factory();

        try (var child = (HarnessAgent) factory.create(parent);
             var same = (HarnessAgent) factory.create(parent);
             var differentUser = (HarnessAgent) factory.create(otherUser);
             var differentSession = (HarnessAgent) factory.create(otherSession);
             var differentRole = (HarnessAgent) entries.getLast().factory().create(parent)) {
            var context = RuntimeContext.empty();
            var filesystem = child.getWorkspaceManager().getFilesystem();
            assertThat(filesystem.write(context, "isolation.md", "test only").error()).isNull();
            assertThat(same.getWorkspaceManager().getFilesystem()
                    .read(context, "isolation.md", 0, 10).fileData()).isNotNull();
            for (var isolated : List.of(differentUser, differentSession, differentRole)) {
                assertThat(isolated.getWorkspaceManager().getFilesystem()
                        .read(context, "isolation.md", 0, 10).fileData()).isNull();
            }
            assertThat(Files.exists(workspace.resolve("isolation.md"))).isFalse();
            assertThat(Files.exists(workspace.resolve("subagents/markdown-tester.md"))).isFalse();
        }
    }
}
