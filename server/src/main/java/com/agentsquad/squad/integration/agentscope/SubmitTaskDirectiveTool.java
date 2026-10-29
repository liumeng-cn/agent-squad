package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.dto.TaskDirectiveSubmit;
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
 * 用户目标修改受理工具。
 * <p>
 * 原生运行身份决定归属与权限，模型参数不能替换用户或会话；数据库操作不等待后续模型执行。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class SubmitTaskDirectiveTool implements AgentTool {
    @Resource
    private TaskDirectiveService directives;

    @Resource
    private ObjectMapper json;

    @Override
    public String getName() {
        return "submit_task_directive";
    }

    @Override
    public String getDescription() {
        return "将当前用户对在途目标的补充或替换要求持久提交。查询进度不调用此工具；ACCEPTED仅表示收到，未应用不能声称已生效。归属与原文由服务端决定。";
    }

    @Override
    public Map<String, Object> getParameters() {
        var schema = new java.util.LinkedHashMap<String, Object>(Map.of("type", "object", "properties", Map.of("logicalKey", Map.of("type", "string", "maxLength", 160), "taskId", Map.of("type", "string", "format", "uuid"), "expectedVersion", Map.of("type", "integer", "minimum", 0), "kind", Map.of("type", "string", "enum", List.of("SUPPLEMENT", "REPLACE")), "content", Map.of("type", "string", "maxLength", 20000)), "required", List.of("logicalKey", "taskId", "expectedVersion", "kind", "content")));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        var fields = Set.of("logicalKey", "taskId", "expectedVersion", "kind", "content");
        var input = param.getInput();
        if (context == null || !fields.containsAll(input.keySet())
                || !input.keySet().containsAll(List.of("logicalKey", "taskId", "expectedVersion", "kind", "content"))
                || !(input.get("expectedVersion") instanceof Number version)
                || version.longValue() < 0 || version.doubleValue() != version.longValue()
                || input.entrySet().stream().anyMatch(entry -> !"expectedVersion".equals(entry.getKey()) && !(entry.getValue() instanceof String))) {
            return ToolResultBlock.error("目标修改参数或运行上下文无效");
        }
        try {
            var request = json.convertValue(input, TaskDirectiveSubmit.class);
            return ToolResultBlock.text(RuntimePayloads.toJson(json, directives.submit(context.getUserId(), context.getSessionId(), request)));
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
