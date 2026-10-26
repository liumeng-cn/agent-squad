package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.dto.PlanReceipt;
import com.agentsquad.squad.runtime.service.PlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * AgentScope 暴露给 Master 的计划发布工具。
 * <p>
 * 把 Master 输出的节点图持久化为不可变 Plan 与 Job；工具结果只是回执，不等待任何子节点执行。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class PublishPlanTool implements AgentTool {

    @Resource
    private PlanService planService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "publish_plan";
    }

    @Override
    public String getDescription() {
        return "需要多个 Worker 协作时调用。提交不可变的节点图（串行依赖、并行与显式汇合）；"
                + "返回 planId 和 jobIds，不等待执行完成。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "logicalKey", Map.of("type", "string", "minLength", 1, "maxLength", 160),
                        "expectedPlanVersion", Map.of("type", "integer", "minimum", 0),
                        "joinPolicy", Map.of("type", "string", "enum", List.of("ALL_REQUIRED", "BEST_EFFORT")),
                        "acceptanceCriteria", Map.of("type", "array",
                                "items", Map.of("type", "string"), "minItems", 1),
                        "nodes", Map.of("type", "array", "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "nodeKey", Map.of("type", "string"),
                                        "workerKey", Map.of("type", "string"),
                                        "workerVersion", Map.of("type", "string"),
                                        "goal", Map.of("type", "string"),
                                        "contextSnapshotId", Map.of("type", "string"),
                                        "dependsOn", Map.of("type", "array", "items", Map.of("type", "string")),
                                        "required", Map.of("type", "boolean"),
                                        "maxRecoveryAttempts", Map.of("type", "integer", "minimum", 0, "maximum", 3)),
                                "required", List.of("nodeKey", "workerKey", "workerVersion", "goal",
                                        "contextSnapshotId", "dependsOn", "required", "maxRecoveryAttempts"))),
                "required", List.of("logicalKey", "expectedPlanVersion", "nodes", "joinPolicy", "acceptanceCriteria"),
                "additionalProperties", false));
    }

    /**
     * 数据库调用放在 boundedElastic；业务拒绝作为工具错误反馈模型，不泄露计划正文。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> submit(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock submit(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null) {
            return ToolResultBlock.error("缺少原生运行上下文");
        }
        PlanPublish publish;
        try {
            publish = objectMapper.convertValue(param.getInput(), PlanPublish.class);
        } catch (IllegalArgumentException exception) {
            return ToolResultBlock.error("计划参数结构无效");
        }
        try {
            PlanReceipt receipt = planService.publish(context.getUserId(),
                    context.getSessionId(), publish);
            return ToolResultBlock.text("计划已发布。planId=" + receipt.planId()
                    + " planVersion=" + receipt.planVersion() + " jobIds=" + receipt.jobIds());
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
