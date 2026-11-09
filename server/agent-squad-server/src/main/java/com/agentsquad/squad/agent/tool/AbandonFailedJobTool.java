package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.bo.AbandonFailedJobRequestBO;
import com.agentsquad.squad.service.WorkerFailureResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协调会话的工作放弃工具
 * <p>
 * 只允许明确放弃已经停止且恢复受限的当前节点，不停止正在运行的工作，不绕过未知外部结果核对。
 */
@Component
public class AbandonFailedJobTool implements AgentTool {
    @Resource
    private WorkerFailureResolutionService resolutions;
    @Resource
    private ObjectMapper json;

    @Override
    public String getName() {
        return "abandon_failed_job";
    }

    @Override
    public String getDescription() {
        return "仅用于当前目标协调：确认无法继续时，明确放弃RECOVERY_BLOCKED工作。"
                + "jobId必须来自当前计划结果，expectedPlanVersion必须是当前计划版本；仍在执行或有未知外部影响时拒绝。"
                + "可恢复时优先重规划，不得只为绕过完成校验而放弃。工具成功只表示该工作未完成，不代表整个目标成功。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(
                        "jobId", Map.of("type", "string", "format", "uuid"),
                        "expectedPlanVersion", Map.of("type", "integer", "minimum", 1)),
                "required", List.of("jobId", "expectedPlanVersion"), "additionalProperties", false);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            var context = param.getRuntimeContext();
            if (context == null || param.getInput() == null || !param.getInput().keySet().equals(Set.of("jobId", "expectedPlanVersion"))) {
                return ToolResultBlock.error("工作处置参数或运行上下文无效");
            }
            try {
                var request = json.convertValue(param.getInput(), AbandonFailedJobRequestBO.class);
                return ToolResultBlock.text(RuntimePayloads.toJson(json, resolutions.abandon(context.getUserId(), context.getSessionId(), request)));
            } catch (RuntimeBusinessException | IllegalArgumentException rejected) {
                return ToolResultBlock.error(rejected.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
