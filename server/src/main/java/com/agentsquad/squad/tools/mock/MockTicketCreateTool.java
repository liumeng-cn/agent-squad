package com.agentsquad.squad.tools.mock;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.tools.operation.OperationService;
import com.agentsquad.squad.tools.operation.PrepareRequest;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.domain.OperationStatus;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 受控写入工具（D2 演示）。预检入账后挂起等待用户审批；
 * 审批通过由平台派发连接器并以外部执行结果续回原回合，工具本体不会重复执行。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class MockTicketCreateTool implements AgentTool {

    private static final String TURN_ID_KEY = "agentscope.session.turnId";
    private static final long RESERVED_UNITS = 1000000L;

    @Resource
    private OperationService operationService;

    @Override
    public String getName() {
        return "mock_ticket_create";
    }

    @Override
    public String getDescription() {
        return "在模拟外部系统创建工单（写入操作）。调用后平台会请求用户审批；未经审批不会执行。"
                + "intentKey 是本次业务写入的幂等键，同一业务重试必须复用同一 intentKey。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "intentKey", Map.of("type", "string", "minLength", 1, "maxLength", 160),
                        "title", Map.of("type", "string", "minLength", 1, "maxLength", 200),
                        "detail", Map.of("type", "string", "minLength", 1, "maxLength", 2000)),
                "required", List.of("intentKey", "title", "detail"),
                "additionalProperties", false);
    }

    /**
     * 写操作只做预检；挂起交给原生 external_execution，执行不在工具内。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> prepare(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock prepare(ToolCallParam param) {
        var context = param.getRuntimeContext();
        Object intentKey = param.getInput().get("intentKey");
        Object title = param.getInput().get("title");
        Object detail = param.getInput().get("detail");
        if (context == null || param.getToolUseBlock() == null
                || !(intentKey instanceof String intent) || !(title instanceof String titleText)
                || !(detail instanceof String detailText)) {
            return ToolResultBlock.error("缺少运行上下文或参数类型错误");
        }
        String turnId = context.get(TURN_ID_KEY, String.class);
        try {
            OperationRow operation = operationService.prepare(new PrepareRequest(
                    context.getUserId(), context.getSessionId(), turnId,
                    param.getToolUseBlock().getId(), getName(), "v1", "WRITE",
                    Map.of("title", titleText, "detail", detailText),
                    intent, true, null, 0L, RESERVED_UNITS, "model"));
            if (!OperationStatus.WAITING_APPROVAL.name().equals(operation.status())) {
                // 命中原意图或原调用：结果已在账本中，直接回执，不再挂起。
                return ToolResultBlock.text("该写入已在账本中（状态 " + operation.status()
                        + "），等待平台续回结果，无需重复提交。");
            }
            log.info("写入操作等待审批 operationId={} toolCallId={} intentKey={}",
                    operation.operationId(), param.getToolUseBlock().getId(), intent);
            throw new ToolSuspendException("工单创建需要用户在页面上审批后才会执行");
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
