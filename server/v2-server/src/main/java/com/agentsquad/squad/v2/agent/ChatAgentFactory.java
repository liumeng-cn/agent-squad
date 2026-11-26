package com.agentsquad.squad.v2.agent;

import com.agentsquad.squad.v2.agent.tool.AskUserTool;
import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.bo.CompiledMasterReleaseBO;
import com.agentsquad.squad.v2.bo.CompiledSubagentSpecBO;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.AgentTool;
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
 * 支持按静态默认配置或已发布的 Master 定格快照独立装配 HarnessAgent；
 * 当 Master 未配置任何子 Agent 时显式禁用子智能体工具组，保证零组件独立对话模式纯净运行。
 */
@Component
public class ChatAgentFactory {
    private static final int DEFAULT_SUB_MAX_ITERS = 16;

    private static final String ASK_USER_PROTOCOL = "\n需要用户补充必要信息、选择或确认时，必须调用 ask_user，"
            + "不要仅用普通回复提问或代替用户回答。每次只询问一个明确问题，收到真实答案后继续；"
            + "用户拒绝时遵守其选择。普通解释和无需用户决策的任务直接完成。";

    @Resource
    private DataSource dataSource;

    /**
     * 创建内置默认主 Agent（兼容历史会话与无发布配置时的兜底运行）
     */
    public HarnessAgent create(ChatAgentProperties properties, List<SubagentDeclaration> subagents,
                               List<? extends MiddlewareBase> middlewares) {
        String agentId = properties.getAgentId();
        Model model = buildModel(properties);

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new EventTestTool());
        toolkit.registerTool(new AskUserTool());

        var dialect = AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build();
        var store = JdbcDistributedStore.create(dataSource, dialect);
        var journal = buildJournal(store);
        var workspace = buildWorkspace(store, agentId);

        Path workspacePath = Path.of(properties.getWorkspace());
        var bus = new WorkspaceMessageBus(workspace, "bus");

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .agentId(agentId)
                .name(properties.getAgentName())
                .sysPrompt(properties.getSystemPrompt() + ASK_USER_PROTOCOL)
                .model(model)
                .toolkit(toolkit)
                .workspace(workspacePath)
                .abstractFilesystem(workspace)
                .sessionLogStore(new WorkspaceSessionLogStore(journal))
                .messageBus(bus)
                .disableFilesystemTools()
                .disableShellTool()
                .enableAgentTracingLog(false)
                .middlewares(middlewares);

        if (subagents == null || subagents.isEmpty()) {
            builder.disableSubagents();
        } else {
            builder.subagents(subagents).disableDynamicSubagents();
        }
        return builder.build();
    }

    /**
     * 从已发布的 Master 定格快照组装生产运行时 HarnessAgent
     * <p>
     * 使用固定 agentCode 作为逻辑标识，使同一 Master 升级新 Release 后仍能读取既有会话历史；
     * 仅注入平台 ask_user 与本次快照定格的工具和子 Agent，不暴露未配置的外部工具。
     */
    public HarnessAgent createFromRelease(ChatAgentProperties properties,
                                          CompiledMasterReleaseBO release,
                                          List<? extends MiddlewareBase> middlewares) {
        String agentCode = release.getAgentCode();
        Model model = buildModel(properties);

        Toolkit masterToolkit = new Toolkit();
        masterToolkit.registerTool(new AskUserTool());
        for (AgentTool tool : release.getDirectTools()) {
            masterToolkit.registerAgentTool(tool);
        }

        var dialect = AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build();
        var store = JdbcDistributedStore.create(dataSource, dialect);
        var journal = buildJournal(store);
        var workspace = buildWorkspace(store, agentCode);

        Path workspacePath = Path.of(properties.getWorkspace());
        var bus = new WorkspaceMessageBus(workspace, "bus");

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .agentId(agentCode)
                .name(release.getDisplayName())
                .sysPrompt(release.getSystemPrompt() + ASK_USER_PROTOCOL)
                .model(model)
                .toolkit(masterToolkit)
                .workspace(workspacePath)
                .abstractFilesystem(workspace)
                .sessionLogStore(new WorkspaceSessionLogStore(journal))
                .messageBus(bus)
                .disableFilesystemTools()
                .disableShellTool()
                .disableWebTools()
                .disableDefaultWorkspaceSkills()
                .disableDynamicSkills()
                .enableAgentTracingLog(false)
                .middlewares(middlewares);

        if (release.getMaxIters() != null && release.getMaxIters() > 0) {
            builder.maxIters(release.getMaxIters());
        }

        if (release.getSubagents().isEmpty()) {
            // Master 未挂载任何子 Agent 时彻底关闭子智能体工具组，纯净独立运行
            builder.disableSubagents();
        } else {
            builder.disableDynamicSubagents();
            for (CompiledSubagentSpecBO subSpec : release.getSubagents()) {
                Toolkit subToolkit = new Toolkit();
                for (AgentTool subTool : subSpec.getTools()) {
                    subToolkit.registerAgentTool(subTool);
                }
                String subName = subSpec.getDelegationName();
                String subDesc = subSpec.getDelegationDescription() == null ? "" : subSpec.getDelegationDescription();
                builder.subagentFactory(subName, subDesc, childSessionKey -> {
                    RemoteFilesystem childFs = new RemoteFilesystem(store.baseStore(), List.of(
                            "squad", "chat", "children-v1", agentCode,
                            Objects.requireNonNullElse(childSessionKey, "sub"), subName));
                    return HarnessAgent.builder()
                            .name(subName)
                            .description(subDesc)
                            .sysPrompt(subSpec.getSystemPrompt())
                            .model(model)
                            .toolkit(subToolkit.copy())
                            .workspace(workspacePath)
                            .abstractFilesystem(childFs)
                            .sessionLogStore(new WorkspaceSessionLogStore(childFs))
                            .maxIters(subSpec.getMaxIters() != null ? subSpec.getMaxIters() : DEFAULT_SUB_MAX_ITERS)
                            .disableFilesystemTools()
                            .disableShellTool()
                            .disableWebTools()
                            .disableDefaultWorkspaceSkills()
                            .disableDynamicSkills()
                            .disableSubagents()
                            .enableAgentTracingLog(false)
                            .build();
                });
            }
        }
        return builder.build();
    }

    private Model buildModel(ChatAgentProperties properties) {
        var options = GenerateOptions.builder();
        if (properties.getSparkThinkingMode() != null) {
            options.additionalBodyParam("thinking",
                    Map.of("type", properties.getSparkThinkingMode().getValue()));
        }
        return OpenAIChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .stream(true)
                .generateOptions(options.build())
                .build();
    }

    private RemoteFilesystem buildJournal(JdbcDistributedStore store) {
        return new RemoteFilesystem(store.baseStore(), context -> List.of(
                "squad", "chat", "journal-v1", "users",
                Objects.requireNonNullElse(context.getUserId(), "")));
    }

    private RemoteFilesystem buildWorkspace(JdbcDistributedStore store, String agentKey) {
        return new RemoteFilesystem(store.baseStore(), context -> List.of(
                "squad", "chat", "workspace-v1",
                Objects.requireNonNullElse(context.getUserId(), ""),
                agentKey, Objects.requireNonNullElse(context.getSessionId(), "")));
    }
}
