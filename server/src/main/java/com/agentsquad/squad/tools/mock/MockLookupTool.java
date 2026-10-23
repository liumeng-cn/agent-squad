package com.agentsquad.squad.tools.mock;

import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
import com.agentsquad.squad.tools.operation.OperationExecutor;
import com.agentsquad.squad.tools.operation.OperationService;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 受控只读工具（D2 演示）。参数与结果都经过操作账本；模型不能绕开账本直连资源。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class MockLookupTool implements AgentTool {

    private static final String TURN_ID_KEY = "agentscope.session.turnId";
    private static final long RESERVED_UNITS = 1000L;

    @Resource
    private OperationService operationService;

    @Resource
    private OperationExecutor operationExecutor;

    @Resource
    private MockTicketConnector mockTicketConnector;

    @Override
    public String getName() {
        return "mock_lookup";
    }

    @Override
    public String getDescription() {
        return "查询受控资源目录的稳定摘要（只读，无副作用）。用于演示受控读取链路。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "resource", Map.of("type", "string", "minLength", 1, "maxLength", 80),
                        "key", Map.of("type", "string", "minLength", 1, "maxLength", 160)),
                "required", List.of("resource", "key"),
                "additionalProperties", false);
    }

    /** 读操作直接执行并出账；账本异常作为工具错误反馈模型。 */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> lookup(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock lookup(ToolCallParam param) {
        var context = param.getRuntimeContext();
        Object resource = param.getInput().get("resource");
        Object key = param.getInput().get("key");
        if (context == null || param.getToolUseBlock() == null
                || !(resource instanceof String resourceName) || !(key instanceof String keyValue)) {
            return ToolResultBlock.error("缺少运行上下文或参数类型错误");
        }
        String turnId = context.get(TURN_ID_KEY, String.class);
        try {
            OperationRow operation = operationService.prepare(new OperationService.PrepareRequest(
                    context.getUserId(), context.getSessionId(), turnId,
                    param.getToolUseBlock().getId(), getName(), "v1", "READ",
                    Map.of("resource", resourceName, "key", keyValue),
                    null, false, null, 0L, RESERVED_UNITS, "model"));
            String resultJson = operationExecutor.execute(operation,
                    externalKey -> mockTicketConnector.lookup(resourceName, keyValue));
            operationService.settle(operation, RESERVED_UNITS);
            return ToolResultBlock.text("查询成功: " + resultJson);
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
