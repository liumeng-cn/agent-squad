package com.agentsquad.squad.lab.agentscope;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;

import java.nio.file.Path;

/**
 * AgentScope 学习实验公共配置与事件输出工具。
 *
 * <p>模型凭据从环境变量读取，工作区使用独立目录，避免触碰 AgentSquad 业务数据。</p>
 */
public final class AgentScopeLabSupport {

    private static final String AGENT_ID = "agentscope-java-lab";
    private static final String USER_ID = "learning-user";
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-plus";

    private AgentScopeLabSupport() {
    }

    /**
     * 创建统一的 HarnessAgent Builder；每个实验请求单独 build 并关闭 Agent。
     */
    public static HarnessAgent.Builder newBuilder() {
        String apiKey = requiredEnvironment("AGENTSCOPE_API_KEY");
        String baseUrl = environmentOrDefault("AGENTSCOPE_BASE_URL", DEFAULT_BASE_URL);
        String modelName = environmentOrDefault("AGENTSCOPE_MODEL", DEFAULT_MODEL);
        Path workspace = Path.of(environmentOrDefault(
                "AGENTSCOPE_WORKSPACE",
                "server/agent-squad-agentscope-lab/.agentscope-lab/workspace"));

        return HarnessAgent.builder()
                .agentId(AGENT_ID)
                .name("AgentScope Java 学习助手")
                .sysPrompt("你是一名耐心的 Java AgentScope 学习助手。"
                        + "回答要准确、简洁，优先用 Java 代码和执行过程解释概念。"
                        + "区分框架原生能力与应用自己实现的能力，不确定时明确说明。")
                .model(OpenAIChatModel.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .stream(true)
                        .build())
                .workspace(workspace);
    }

    /**
     * 为实验调用设置稳定的用户身份和指定会话身份。
     */
    public static RuntimeContext runtimeContext(String sessionId) {
        return RuntimeContext.builder()
                .userId(USER_ID)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 订阅 Agent 事件流，只把文本增量和工具调用开始事件打印到控制台。
     */
    public static void stream(HarnessAgent agent, String sessionId, String prompt) {
        agent.streamEvents(new UserMessage(prompt), runtimeContext(sessionId))
                .doOnNext(event -> {
                    if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        System.out.print(((TextBlockDeltaEvent) event).getDelta());
                    } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                        ToolCallStartEvent toolEvent = (ToolCallStartEvent) event;
                        System.out.printf("%n[工具调用] %s%n", toolEvent.getToolCallName());
                    }
                })
                .blockLast();
        System.out.println();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 " + name);
        }
        return value;
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
