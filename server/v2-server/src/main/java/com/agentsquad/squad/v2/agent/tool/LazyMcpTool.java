package com.agentsquad.squad.v2.agent.tool;

import com.agentsquad.squad.v2.agent.mcp.SharedMcpClientHandle;
import com.agentsquad.squad.v2.agent.tool.http.HttpToolTemplate;
import com.agentsquad.squad.v2.bo.ToolDefinitionBO;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * 懒连接 MCP 运行时工具
 * <p>
 * 注册进 AgentScope Toolkit 时直接使用定格快照中的工具名称、说明与 JSON Schema，不提前建连；
 * 模型实际发起调用时通过共享句柄建立或复用 MCP 连接，并对错误输出中的凭据做脱敏保护。
 */
public class LazyMcpTool implements AgentTool {
    private final ToolDefinitionBO definition;

    private final ConfiguredToolFactory toolFactory;

    private final SharedMcpClientHandle clientHandle;

    public LazyMcpTool(ToolDefinitionBO definition, ConfiguredToolFactory toolFactory, SharedMcpClientHandle clientHandle) {
        this.definition = definition;
        this.toolFactory = toolFactory;
        this.clientHandle = clientHandle;
    }

    @Override
    public String getName() {
        return definition.getExposedName();
    }

    @Override
    public String getDescription() {
        return definition.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return definition.getInputSchema();
    }

    @Override
    public boolean isReadOnly() {
        return definition.isReadOnly();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String credential = clientHandle.getConnection().getCredential();
        return Mono.defer(() -> {
            McpClientWrapper client = clientHandle.acquire();
            AgentTool delegate = toolFactory.createMcpTool(definition, client);
            return delegate.callAsync(param)
                    .doOnError(error -> clientHandle.invalidate(client));
        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    String raw = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    return Mono.just(ToolResultBlock.error("调用远程 MCP 工具失败：" + HttpToolTemplate.mask(raw, credential)));
                });
    }
}
