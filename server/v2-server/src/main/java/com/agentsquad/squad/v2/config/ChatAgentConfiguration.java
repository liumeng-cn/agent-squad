package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.agent.ChatAgentFactory;
import com.agentsquad.squad.v2.agent.tracing.FrameworkTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ModelIoTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ReasoningTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ToolCallTracingMiddleware;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.HarnessAgent;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 默认聊天 Agent 配置
 * <p>
 * 通过工厂创建默认 Master，由 Spring 管理该实例的生命周期。
 */
@Configuration
@EnableConfigurationProperties(ChatAgentProperties.class)
public class ChatAgentConfiguration {
    @Resource
    private ChatAgentProperties properties;

    @Resource
    private ChatAgentFactory chatAgentFactory;

    /**
     * 创建默认 Agent
     * <p>
     * 观测启用时保持原有中间件顺序，由官方 Span 包裹业务观测中间件。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agentsquad.v2.agent", name = "enabled", havingValue = "true")
    public HarnessAgent chatAgent(Optional<OpenTelemetrySdk> observationSdk,
                                  Optional<ModelIoTracingMiddleware> modelTracing,
                                  Optional<ToolCallTracingMiddleware> toolTracing,
                                  Optional<FrameworkTracingMiddleware> frameworkTracing,
                                  Optional<ReasoningTracingMiddleware> reasoningTracing) {
        List<MiddlewareBase> middlewares = new ArrayList<>();
        if (observationSdk.isPresent() && modelTracing.isPresent()) {
            middlewares.add(new OtelTracingMiddleware(observationSdk.get()));
            middlewares.add(modelTracing.get());
            toolTracing.ifPresent(middlewares::add);
            frameworkTracing.ifPresent(middlewares::add);
            reasoningTracing.ifPresent(middlewares::add);
        }

        return chatAgentFactory.create(properties, List.of(), middlewares);
    }
}
