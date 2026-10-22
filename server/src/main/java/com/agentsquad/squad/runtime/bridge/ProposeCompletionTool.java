package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.GoalTaskStatus;
import com.agentsquad.squad.runtime.dto.CompletionProposalReceipt;
import com.agentsquad.squad.runtime.service.CompletionProposalService;
import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
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
 * AgentScope 暴露给 Master 的完成候选工具。工具结果只是持久回执，不能当作用户最终答案。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class ProposeCompletionTool implements AgentTool {

    private static final String TURN_ID_KEY = "agentscope.session.turnId";

    @Resource
    private CompletionProposalService completionProposalService;

    @Override
    public String getName() {
        return "propose_completion";
    }

    @Override
    public String getDescription() {
        return "完成当前用户目标前必须调用。提交最终回答候选；平台将在本次原生回合成功结束后发布答案。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "outcome", Map.of("type", "string", "enum",
                                List.of(GoalTaskStatus.SUCCEEDED.name(), GoalTaskStatus.PARTIAL.name())),
                        "finalAnswer", Map.of("type", "string", "minLength", 1, "maxLength", 200000)),
                "required", List.of("outcome", "finalAnswer"),
                "additionalProperties", false);
    }

    /** 数据库调用放在 boundedElastic；业务拒绝作为工具错误反馈模型，不泄露答案正文。 */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> submit(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock submit(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null || param.getToolUseBlock() == null) {
            return ToolResultBlock.error("缺少原生运行上下文");
        }
        Object rawOutcome = param.getInput().get("outcome");
        Object rawAnswer = param.getInput().get("finalAnswer");
        if (!(rawOutcome instanceof String outcome) || !(rawAnswer instanceof String answer)) {
            return ToolResultBlock.error("完成候选参数必须是字符串");
        }
        try {
            CompletionProposalReceipt receipt = completionProposalService.propose(
                    context.getUserId(), context.getSessionId(), context.get(TURN_ID_KEY, String.class),
                    param.getToolUseBlock().getId(), outcome, answer);
            return ToolResultBlock.text("完成候选已保存，等待当前回合结束。taskId=" + receipt.taskId());
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
