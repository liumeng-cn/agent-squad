package com.agentsquad.experiments;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无外部副作用的终结及查询工具
 * <p>
 * 用固定成功或错误结果验证 SDK 直接返回条件，调用次数仅表示本地模拟操作。
 */
final class ProbeTool implements AgentTool {
    private final String name;
    private final boolean direct;
    private final boolean error;
    private final AtomicInteger calls = new AtomicInteger();

    ProbeTool(String name, boolean direct, boolean error) {
        this.name = name;
        this.direct = direct;
        this.error = error;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return "本地契约实验工具";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isReturnDirect() {
        return direct;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromSupplier(() -> {
            calls.incrementAndGet();
            return error ? ToolResultBlock.error("实验拒绝") : ToolResultBlock.text("实验工具结果");
        });
    }

    int calls() {
        return calls.get();
    }
}
