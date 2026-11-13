package com.agentsquad.squad.v2.agent.subagent;

import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 子 Agent 测试定义
 * <p>
 * 为聊天 Harness 装配 Markdown 与编程式角色；通用角色使用框架内置定义。
 */
public final class TestSubagentDefinitions {
    private TestSubagentDefinitions() {
    }

    /**
     * 首次启动安装 Markdown 示例，已有文件保持原样；配置失败时终止装配，重启可重试。
     * 编程式角色继承父模型，不创建额外模型连接。
     */
    public static void configure(HarnessAgent.Builder builder, Path workspace) {
        Path target = workspace.resolve("subagents/markdown-tester.md");

        try {
            Files.createDirectories(target.getParent());
            try (var input = TestSubagentDefinitions.class.getResourceAsStream("/subagents/markdown-tester.md")) {
                if (input == null) {
                    throw new IllegalStateException("缺少 Markdown 子 Agent 测试定义");
                }
                try {
                    Files.copy(input, target);
                } catch (FileAlreadyExistsException ignored) {
                    // 工作区定义允许用户修改，重复启动不覆盖。
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("安装 Markdown 子 Agent 测试定义失败", exception);
        }

        builder.subagent(SubagentDeclaration.builder()
                .name("java-tester")
                .description("Java 编程式声明的测试助手，测试程序配置角色时使用")
                .inlineAgentsBody("你是 Java 声明测试助手。回答以[Java子Agent]开头，随后给出简短任务结果。不修改文件，不执行命令。")
                .steps(4)
                .build());
    }
}
