package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.config.properties.SquadV2AgentProperties;
import com.agentsquad.squad.v2.config.properties.SquadV2ExecutionProperties;
import com.agentsquad.squad.v2.stream.AgentScopeEventAdapter;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import jakarta.annotation.Resource;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** V2 AgentScope 配置；HarnessAgent 为应用级单例。 */
@Configuration
@EnableConfigurationProperties({SquadV2AgentProperties.class, SquadV2ExecutionProperties.class})
public class SquadV2AgentConfiguration {
    @Resource private SquadV2AgentProperties properties;

    /** 原生 AgentEvent 转换器。 */
    @Bean
    public AgentScopeEventAdapter agentScopeEventAdapter() { return new AgentScopeEventAdapter(); }

    /** 创建唯一 HarnessAgent；请求级运行状态通过 RuntimeContext 隔离。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agentsquad.v2.agent", name = "enabled", havingValue = "true")
    public HarnessAgent squadV2HarnessAgent() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("已启用 V2 Agent，但未配置 SQUAD_V2_AGENT_API_KEY");
        }
        OpenAIChatModel model = OpenAIChatModel.builder()
            .apiKey(properties.getApiKey()).baseUrl(properties.getBaseUrl())
            .modelName(properties.getModelName()).stream(true).build();
        return HarnessAgent.builder().name(properties.getAgentName())
            .agentId(properties.getAgentId()).sysPrompt(properties.getSystemPrompt())
            .model(model).workspace(Path.of(properties.getWorkspace())).build();
    }
}