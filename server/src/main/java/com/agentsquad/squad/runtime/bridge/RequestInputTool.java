package com.agentsquad.squad.runtime.bridge;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 受控追问工具。问题入账为持久 INPUT 交互后挂起；用户答复由平台以外部执行结果续回，
 * 工具本体不等待、不重复执行。除 question/answerSchema 外不接受其他参数。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class RequestInputTool implements AgentTool {

    @Override
    public String getName() {
        return "request_input";
    }

    @Override
    public String getDescription() {
        return "向用户提出结构化追问（需要用户补充信息时调用）。调用后回合挂起，"
                + "问题会出现在用户页面；不要在同一轮重复追问，也不要假设用户已经回答。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of("type", "string", "minLength", 1, "maxLength", 4000),
                        "answerSchema", Map.of("type", "object",
                                "description", "答复的 JSON Schema 描述，可选；缺省为自由文本")),
                "required", List.of("question"),
                "additionalProperties", false);
    }

    /**
     * 只负责把追问挂起；账本与绑定发生在平台投影层。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> suspend(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock suspend(ToolCallParam param) {
        Object question = param.getInput().get("question");
        if (param.getRuntimeContext() == null || param.getToolUseBlock() == null
                || !(question instanceof String questionText) || questionText.isBlank()
                || questionText.length() > 4000) {
            return ToolResultBlock.error("追问缺少有效问题文本");
        }
        throw new ToolSuspendException(questionText);
    }
}
