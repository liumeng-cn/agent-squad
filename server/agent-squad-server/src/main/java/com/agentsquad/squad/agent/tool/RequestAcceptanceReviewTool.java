package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.domain.dto.AcceptanceReviewRequest;
import com.agentsquad.squad.runtime.service.PlanAcceptanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 请求用户确认当前计划结果
 * <p>
 * 问题由持久完成条件生成，通过 SDK 挂起和现有交互答复流程继续，不能用模型声明替代用户决定。
 */
@Component
public class RequestAcceptanceReviewTool implements AgentTool {
    @Resource
    private PlanAcceptanceService acceptance;

    @Resource
    private ObjectMapper json;

    @Override
    public String getName() {
        return "request_acceptance_review";
    }

    @Override
    public String getDescription() {
        return "当前计划包含USER_REVIEW完成条件时调用，请用户确认实际结果。工作全部收尾后才能请求，调用后挂起等待用户答复。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(
                        "criterionId", Map.of("type", "string", "minLength", 1, "maxLength", 80),
                        "expectedPlanVersion", Map.of("type", "integer", "minimum", 1)),
                "required", List.of("criterionId", "expectedPlanVersion"), "additionalProperties", false);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> suspend(param)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock suspend(ToolCallParam param) {
        if (param.getRuntimeContext() == null || param.getToolUseBlock() == null) {
            return ToolResultBlock.error("缺少可信执行身份");
        }
        try {
            var context = param.getRuntimeContext();
            var review = acceptance.prepareUserReview(context.getUserId(), context.getSessionId(),
                    json.convertValue(param.getInput(), AcceptanceReviewRequest.class));
            throw new ToolSuspendException(review.question());
        } catch (RuntimeBusinessException | IllegalArgumentException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
