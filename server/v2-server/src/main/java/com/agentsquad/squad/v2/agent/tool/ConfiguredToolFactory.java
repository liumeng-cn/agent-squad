package com.agentsquad.squad.v2.agent.tool;

import com.agentsquad.squad.v2.agent.tool.builtin.BuiltinToolRegistry;
import com.agentsquad.squad.v2.agent.tool.builtin.RenamedBuiltinTool;
import com.agentsquad.squad.v2.agent.tool.http.HttpConfiguredTool;
import com.agentsquad.squad.v2.bo.ToolDefinitionBO;
import com.agentsquad.squad.v2.enums.ToolType;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/**
 * 配置化工具工厂
 * <p>
 * 把工具版本配置生成 AgentScope 可执行工具：内置工具包装代码实现，HTTP 工具按模板调用接口，
 * MCP 工具使用 SDK 的 McpTool 调用远程服务。试运行和后续的运行时装配共用这一套生成规则。
 */
@Component
public class ConfiguredToolFactory {
    @Resource
    private BuiltinToolRegistry builtinTools;

    @Resource
    private HttpClient toolHttpClient;

    /**
     * 生成本地执行的工具
     * <p>
     * 适用于内置和 HTTP 工具；MCP 工具需要先建立连接，使用 {@link #createMcpTool}。
     */
    public AgentTool create(ToolDefinitionBO definition) {
        if (definition.getToolType() == ToolType.BUILTIN) {
            AgentTool implementation = builtinTools.create(definition.getImplementationKey());
            return new RenamedBuiltinTool(implementation, definition.getExposedName(), definition.getDescription());
        }
        if (definition.getToolType() == ToolType.HTTP) {
            return new HttpConfiguredTool(definition, toolHttpClient);
        }
        throw new IllegalArgumentException("MCP 工具需要使用已建立的连接生成");
    }

    /**
     * 生成 MCP 工具
     * <p>
     * 模型看到配置的名称、说明和参数，调用时转发为远程工具名；连接由调用方管理和关闭。
     */
    public AgentTool createMcpTool(ToolDefinitionBO definition, McpClientWrapper client) {
        return new McpTool(
                definition.getExposedName(),
                definition.getRemoteToolName(),
                definition.getDescription(),
                definition.getInputSchema(),
                null,
                client,
                null,
                client.getName(),
                definition.isReadOnly());
    }
}
