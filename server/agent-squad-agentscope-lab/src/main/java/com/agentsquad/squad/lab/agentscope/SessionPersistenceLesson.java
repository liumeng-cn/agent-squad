package com.agentsquad.squad.lab.agentscope;

import io.agentscope.harness.agent.HarnessAgent;

/**
 * 第 3 课：关闭并重新创建 Agent 实例，验证同一用户会话能否延续。
 */
public final class SessionPersistenceLesson {

    private SessionPersistenceLesson() {
    }

    public static void main(String[] args) {
        String sessionId = args.length == 0 ? "session-persistence-demo" : args[0];

        System.out.println("第一轮：写入一条会话信息");
        runTurn(sessionId,
                "请记住：我是一名 Java 开发者，今天正在学习 AgentScope Java 2.0，我喜欢通过实际代码理解概念。");

        System.out.println();
        System.out.println("第二轮：重新创建 Agent 实例，再询问上一轮的信息");
        runTurn(sessionId,
                "我刚才说自己从事什么开发？今天在学习什么？我喜欢怎样学习？");
    }

    private static void runTurn(String sessionId, String prompt) {
        try (HarnessAgent agent = AgentScopeLabSupport.newBuilder().build()) {
            AgentScopeLabSupport.stream(agent, sessionId, prompt);
        }
    }
}
