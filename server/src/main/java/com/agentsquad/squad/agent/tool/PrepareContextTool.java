package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.runtime.dto.ContextSnapshotReceipt;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import io.agentscope.core.agent.RuntimeContext;
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
 * Master 的局部上下文准备工具。
 *
 * 由平台读取当前目标的用户原话并冻结节点材料；模型只选择节点键与稳定逻辑键，不能伪造来源。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class PrepareContextTool implements AgentTool {

    @Resource
    private com.fasterxml.jackson.databind.ObjectMapper json;

    @Resource
    private ContextSnapshotService contextSnapshotService;

    @Override
    public String getName() {
        return "prepare_context";
    }

    @Override
    public String getDescription() {
        return "为计划节点冻结当前用户目标的局部材料，返回 contextSnapshotId；同一节点重试须复用 logicalKey。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "targetNodeKey", Map.of("type", "string", "pattern", "^[a-z][a-z0-9_-]{0,79}$"),
                        "logicalKey", Map.of("type", "string", "minLength", 1, "maxLength", 160),
                        "skillVersionIds", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 64, "uniqueItems", true),
                        "memoryIds", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 20, "uniqueItems", true)),
                "required", List.of("targetNodeKey", "logicalKey"),
                "additionalProperties", false);
    }

    /**
     * 数据库调用放在 boundedElastic；只返回快照标识与摘要，不在工具回执复制用户全文。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> prepare(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock prepare(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null) {
            return ToolResultBlock.error("缺少原生运行上下文");
        }
        Object nodeKey = param.getInput().get("targetNodeKey");
        Object logicalKey = param.getInput().get("logicalKey");
        if (!(nodeKey instanceof String node) || !(logicalKey instanceof String key)) {
            return ToolResultBlock.error("局部上下文参数结构无效");
        }
        try {
            ContextSnapshotReceipt receipt = contextSnapshotService.prepare(
                    context.getUserId(), context.getSessionId(), json.convertValue(param.getInput(),
                            com.agentsquad.squad.runtime.dto.ContextPreparationRequest.class));
            return ToolResultBlock.text("局部上下文已准备。contextSnapshotId=" + receipt.snapshotId()
                    + " contentHash=" + receipt.contentHash());
        } catch (IllegalArgumentException exception) {
            return ToolResultBlock.error("局部材料选择格式无效");
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
