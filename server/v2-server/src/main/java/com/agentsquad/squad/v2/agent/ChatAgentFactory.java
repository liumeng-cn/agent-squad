package com.agentsquad.squad.v2.agent;

import com.agentsquad.squad.v2.agent.tool.AskUserTool;
import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.bus.WorkspaceMessageBus;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天 Agent 工厂
 * <p>
 * 根据调用方提供的配置独立装配 Master，创建者负责实例的切换和关闭。
 */
@Component
public class ChatAgentFactory {
    @Resource
    private DataSource dataSource;

    /**
     * 创建主 Agent
     * <p>
     * 每次创建独立的模型和工具集；使用相同逻辑标识时沿用原生会话的存储路径。
     */
    public HarnessAgent create(ChatAgentProperties properties, List<SubagentDeclaration> subagents,
                               List<? extends MiddlewareBase> middlewares) {
        // 固定本次装配的逻辑标识，保持会话与工作区使用同一标识。
        String agentId = properties.getAgentId();

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
                agentId, Objects.requireNonNullElse(context.getSessionId(), "")));

        Path workspacePath = Path.of(properties.getWorkspace());
        var bus = new WorkspaceMessageBus(workspace, "bus");

        return HarnessAgent.builder()
                .agentId(agentId)
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
                .enableAgentTracingLog(false)
                .subagents(subagents)
                .middlewares(middlewares)
                .build();

    }
}
