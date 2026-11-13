package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.model.GenerateOptions;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import com.agentsquad.squad.v2.agent.observer.AgentTracingMiddleware;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 聊天 Agent 装配
 * <p>
 * 使用单个 Harness 管理按用户与会话隔离的原生 AgentSession，不另建执行注册表。
 */
@Configuration
@EnableConfigurationProperties(ChatAgentProperties.class)
public class ChatAgentConfiguration {
    @Resource
    private ChatAgentProperties properties;

    @Resource
    private ObjectProvider<AgentTracingMiddleware> tracingProvider;

    /**
     * 创建框架 Agent；应用关闭时由框架释放会话与运行资源。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agentsquad.v2.agent", name = "enabled", havingValue = "true")
    public HarnessAgent chatAgent() {
        if (properties.getApiKey().isBlank()) {
            throw new IllegalStateException("请配置聊天模型 api-key");
        }
        var options = GenerateOptions.builder();
        if (properties.getSparkThinkingMode() != null) {
            options.additionalBodyParam("thinking",
                    Map.of("type", properties.getSparkThinkingMode().getValue()));
        }
        var model = OpenAIChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .stream(true)
                .generateOptions(options.build())
                .build();
        var toolkit = new Toolkit();
        toolkit.registerTool(new EventTestTool());

        var agent = HarnessAgent.builder()
                .agentId(properties.getAgentId())
                .name(properties.getAgentName())
                .sysPrompt(properties.getSystemPrompt())
                .model(model)
                .toolkit(toolkit)
                .workspace(Path.of(properties.getWorkspace()));
        tracingProvider.ifAvailable(agent::middleware);
        return agent.build();
    }
}
