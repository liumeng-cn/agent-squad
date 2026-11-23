package com.agentsquad.squad.v2.agent.tool.builtin;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 按配置命名的内置工具
 * <p>
 * 内置实现的参数与只读标记保持不变，只按工具版本替换模型可见的名称和用途说明。
 */
public class RenamedBuiltinTool implements AgentTool {
    private final AgentTool delegate;

    private final String name;

    private final String description;

    public RenamedBuiltinTool(AgentTool delegate, String name, String description) {
        this.delegate = delegate;
        this.name = name;
        this.description = description;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return delegate.getParameters();
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return delegate.callAsync(param);
    }
}
