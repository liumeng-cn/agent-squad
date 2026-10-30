package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.dto.GoalTaskCreate;
import com.agentsquad.squad.conversation.service.GoalTaskCreationService;
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
import java.util.Set;

/**
 * 回复Master建立独立目标的受控工具。
 * <p>
 * 可信身份来自SDK上下文，只持久受理，不等待目标或Worker执行完毕；工具回执不是最终成果。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CreateGoalTaskTool implements AgentTool {
    private static final Set<String> FIELDS = Set.of("logicalKey", "title", "objective", "sourceMessageId");

    @Resource
    private GoalTaskCreationService goals;

    @Resource
    private ObjectMapper json;

    @Override
    public String getName() {
        return "create_goal_task";
    }

    @Override
    public String getDescription() {
        return "仅当当前用户目标需要持续推进或委派协作时创建独立目标。普通交流直接回答。"
                + "sourceMessageId必须是当前用户消息，logicalKey同业务重试保持不变；返回持久受理收据，不能声称工作已完成。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(
                        "logicalKey", Map.of("type", "string", "minLength", 1, "maxLength", 160),
                        "title", Map.of("type", "string", "minLength", 1, "maxLength", 200),
                        "objective", Map.of("type", "string", "minLength", 1, "maxLength", 20000),
                        "sourceMessageId", Map.of("type", "string", "format", "uuid")),
                "required", List.of("logicalKey", "title", "objective", "sourceMessageId"), "additionalProperties", false);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> create(param)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock create(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null || !param.getInput().keySet().equals(FIELDS)
                || param.getInput().values().stream().anyMatch(value -> !(value instanceof String))) {
            return ToolResultBlock.error("目标创建参数或原生上下文无效");
        }
        try {
            GoalTaskCreate request = json.convertValue(param.getInput(), GoalTaskCreate.class);
            return ToolResultBlock.text(RuntimePayloads.toJson(json, goals.create(context.getUserId(), context.getSessionId(), request)));
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
