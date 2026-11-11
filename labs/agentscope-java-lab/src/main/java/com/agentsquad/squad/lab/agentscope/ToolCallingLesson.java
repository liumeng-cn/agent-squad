package com.agentsquad.squad.lab.agentscope;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;

/**
 * 第 2 课：通过 Toolkit 注册 Java 方法，让模型自主决定是否调用工具。
 */
public final class ToolCallingLesson {

    private ToolCallingLesson() {
    }

    public static void main(String[] args) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new CalculatorTools());

        HarnessAgent.Builder builder = AgentScopeLabSupport.newBuilder();
        builder.toolkit(toolkit);

        try (HarnessAgent agent = builder.build()) {
            AgentScopeLabSupport.stream(
                    agent,
                    "tool-calling-demo",
                    "请调用 add_numbers 工具计算 17 加 25，并用一句话说明计算结果。不要直接心算。");
        }
    }
}
