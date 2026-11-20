package com.agentsquad.squad.v2.agent.subagent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.middleware.SubagentEntry;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;
import io.agentscope.harness.agent.subagent.AgentSpecLoader;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 聊天子 Agent 定义
 * <p>
 * 装配内置通用角色、发布资源角色和编程式角色，存储按父用户和会话隔离。
 */
public final class TestSubagentDefinitions {

    private TestSubagentDefinitions() {
    }

    public static List<SubagentEntry> entries(Model model, Toolkit toolkit, Path workspace, BaseStore store) {
        var general = SubagentDeclaration.builder()
                .name("general-purpose")
                .description("处理可完整委派的通用任务")
                .inlineAgentsBody("You are a highly capable general-purpose subagent.")
                .build();
        var markdown = markdown(workspace);
        var java = SubagentDeclaration.builder()
                .name("java-tester")
                .description("Java 编程式声明的测试助手，测试程序配置角色时使用")
                .inlineAgentsBody("你是 Java 声明测试助手。回答以[Java子Agent]开头，随后给出简短任务结果。不修改文件，不执行命令。")
                .steps(4)
                .build();

        return List.of(
                entry(general, model, toolkit, workspace, store),
                entry(markdown, model, new Toolkit(), workspace, store),
                entry(java, model, new Toolkit(), workspace, store));
    }

    private static SubagentDeclaration markdown(Path workspace) {
        try (var input = TestSubagentDefinitions.class.getResourceAsStream("/subagents/markdown-tester.md")) {
            if (input == null) {
                throw new IllegalStateException("缺少 Markdown 子 Agent 定义");
            }
            String definition = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return AgentSpecLoader.parse(definition, "markdown-tester", workspace);
        } catch (IOException exception) {
            throw new UncheckedIOException("无法读取 Markdown 子 Agent 定义", exception);
        }
    }

    private static SubagentEntry entry(SubagentDeclaration declaration, Model model, Toolkit toolkit,
                                       Path workspace, BaseStore store) {
        return new SubagentEntry(declaration.getName(), declaration.getDescription(),
                parent -> build(declaration, model, toolkit, workspace, isolatedFilesystem(store, parent,
                        declaration.getName())), declaration);
    }

    private static RemoteFilesystem isolatedFilesystem(BaseStore store, RuntimeContext parent, String role) {
        var namespace = List.of("squad", "chat", "children-v1",
                Objects.requireNonNullElse(parent.getUserId(), ""),
                Objects.requireNonNullElse(parent.getSessionId(), ""), role);
        return new RemoteFilesystem(store, namespace);
    }

    private static HarnessAgent build(SubagentDeclaration declaration, Model model, Toolkit toolkit,
                                      Path workspace, RemoteFilesystem filesystem) {
        return HarnessAgent.builder()
                .name(declaration.getName())
                .description(declaration.getDescription())
                .sysPrompt(declaration.getInlineAgentsBody())
                .model(model)
                .toolkit(toolkit.copy())
                .workspace(workspace)
                .abstractFilesystem(filesystem)
                .sessionLogStore(new WorkspaceSessionLogStore(filesystem))
                .maxIters(declaration.getSteps())
                .disableSubagents()
                .build();
    }
}
