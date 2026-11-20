package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.agent.tracing.FrameworkTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ModelIoTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ReasoningTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ToolCallTracingMiddleware;
import com.agentsquad.squad.v2.agent.tool.AskUserTool;
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
import org.springframework.beans.factory.ObjectProvider;
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
    private DataSource dataSource;

    @Resource
    private ObjectProvider<ModelIoTracingMiddleware> modelTracing;

    @Resource
    private ObjectProvider<ToolCallTracingMiddleware> toolTracing;

    @Resource
    private ObjectProvider<FrameworkTracingMiddleware> frameworkTracing;

    @Resource
    private ObjectProvider<ReasoningTracingMiddleware> reasoningTracing;

    /**
     * 创建框架 Agent；应用关闭时由框架释放会话与运行资源。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agentsquad.v2.agent", name = "enabled", havingValue = "true")
    public HarnessAgent chatAgent(ObjectProvider<OpenTelemetrySdk> observationSdkProvider) {

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
        toolkit.registerTool(new AskUserTool());

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
                .sysPrompt(properties.getSystemPrompt() + "\n需要用户补充必要信息、选择或确认时，必须调用 ask_user，"
                        + "不要仅用普通回复提问或代替用户回答。每次只询问一个明确问题，收到真实答案后继续；"
                        + "用户拒绝时遵守其选择。普通解释和无需用户决策的任务直接完成。")
                .model(model)
                .toolkit(toolkit)
                .workspace(workspacePath)
                .abstractFilesystem(workspace)
                .sessionLogStore(new WorkspaceSessionLogStore(journal))
                .messageBus(bus)
                .disableFilesystemTools()
                .disableShellTool()
                .enableAgentTracingLog(false);

        // 观测是可选能力：未开启时不创建追踪中间件，也不要求 Langfuse 服务可用。
        OpenTelemetrySdk observationSdk = observationSdkProvider.getIfAvailable();
        if (observationSdk != null) {
            agent.middleware(new OtelTracingMiddleware(observationSdk));
            // 同级按注册顺序形成洋葱链；官方 Span 在外层，业务中间件才取得其上下文。
            agent.middleware(modelTracing.getObject());
            agent.middleware(toolTracing.getObject());
            agent.middleware(frameworkTracing.getObject());
            agent.middleware(reasoningTracing.getObject());
        }
        return agent.build();
    }
}
