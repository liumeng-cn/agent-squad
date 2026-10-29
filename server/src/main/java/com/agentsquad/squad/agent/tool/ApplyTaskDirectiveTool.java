package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.dto.TaskDirectiveDecision;
import com.agentsquad.squad.conversation.service.TaskDirectiveService;
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
 * 目标协调者的修改裁决工具。
 * <p>
 * 原生运行身份决定归属与权限，模型参数不能替换用户或会话；数据库操作不等待后续模型执行。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class ApplyTaskDirectiveTool implements AgentTool {
    @Resource
    private TaskDirectiveService directives;

    @Resource
    private ObjectMapper json;

    @Override
    public String getName() {
        return "apply_task_directive";
    }

    @Override
    public String getDescription() {
        return "仅原目标协调会话可应用或拒绝修改。APPLY安全点校验失败时保持ACCEPTED；REJECT必须说明原因。以回执确认状态，不得重放未知写入。";
    }

    @Override
    public Map<String, Object> getParameters() {
        var schema = new java.util.LinkedHashMap<String, Object>(Map.of("type", "object", "properties", Map.of("directiveId", Map.of("type", "string", "format", "uuid"), "expectedVersion", Map.of("type", "integer", "minimum", 0), "decision", Map.of("type", "string", "enum", List.of("APPLY", "REJECT")), "reason", Map.of("type", "string", "maxLength", 500)), "required", List.of("directiveId", "expectedVersion", "decision")));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        var fields = Set.of("directiveId", "expectedVersion", "decision", "reason");
        var input = param.getInput();
        if (context == null || !fields.containsAll(input.keySet())
                || !input.keySet().containsAll(List.of("directiveId", "expectedVersion", "decision"))
                || !(input.get("expectedVersion") instanceof Number version)
                || version.longValue() < 0 || version.doubleValue() != version.longValue()
                || input.entrySet().stream().anyMatch(entry -> !"expectedVersion".equals(entry.getKey()) && !(entry.getValue() instanceof String))) {
            return ToolResultBlock.error("目标修改参数或运行上下文无效");
        }
        try {
            var request = json.convertValue(input, TaskDirectiveDecision.class);
            return ToolResultBlock.text(RuntimePayloads.toJson(json, directives.decide(context.getUserId(), context.getSessionId(), request)));
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
