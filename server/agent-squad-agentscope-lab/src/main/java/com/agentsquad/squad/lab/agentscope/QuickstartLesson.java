package com.agentsquad.squad.lab.agentscope;

import io.agentscope.harness.agent.HarnessAgent;

/**
 * 第 1 课：创建 HarnessAgent，并观察 AgentScope 原生事件流。
 */
public final class QuickstartLesson {

    private QuickstartLesson() {
    }

    public static void main(String[] args) {
        String prompt = args.length == 0
                ? "用三句话介绍 AgentScope Java 2.0 的核心定位。"
                : String.join(" ", args);

        try (HarnessAgent agent = AgentScopeLabSupport.newBuilder().build()) {
            AgentScopeLabSupport.stream(agent, "quickstart-demo", prompt);
        }
    }
}
