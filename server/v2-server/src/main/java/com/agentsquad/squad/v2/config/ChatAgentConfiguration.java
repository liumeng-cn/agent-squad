package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.agent.middleware.LangfuseEnrichmentMiddleware;
import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.bus.WorkspaceMessageBus;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private LangfuseEnrichmentMiddleware tracingMiddleware;

    @Resource
    private DataSource dataSource;

    /**
     * 创建框架 Agent；应用关闭时由框架释放会话与运行资源。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agentsquad.v2.agent", name = "enabled", havingValue = "true")
    public HarnessAgent chatAgent(OpenTelemetrySdk observationSdk) {

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

        var dialect = AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build();
        var store = JdbcDistributedStore.create(dataSource, dialect);
        var journal = new RemoteFilesystem(store.baseStore(), context -> List.of(
                "squad", "chat", "journal-v1", "users",
                Objects.requireNonNullElse(context.getUserId(), "")));
        var workspace = new RemoteFilesystem(store.baseStore(), context -> List.of(
                "squad", "chat", "workspace-v1",
                Objects.requireNonNullElse(context.getUserId(), ""),
                properties.getAgentId(), Objects.requireNonNullElse(context.getSessionId(), "")));

        Path workspacePath = Path.of(properties.getWorkspace());
        var bus = new WorkspaceMessageBus(workspace, "bus");

        var agent = HarnessAgent.builder()
                .agentId(properties.getAgentId())
                .name(properties.getAgentName())
                .sysPrompt(properties.getSystemPrompt())
                .model(model)
                .toolkit(toolkit)
                .workspace(workspacePath)
                .abstractFilesystem(workspace)
                .sessionLogStore(new WorkspaceSessionLogStore(journal))
                .messageBus(bus)
                .disableFilesystemTools()
                .disableShellTool()
                .enableAgentTracingLog(true)
                .middleware(new OtelTracingMiddleware(observationSdk))
                .middleware(tracingMiddleware);
        return agent.build();
    }

}
