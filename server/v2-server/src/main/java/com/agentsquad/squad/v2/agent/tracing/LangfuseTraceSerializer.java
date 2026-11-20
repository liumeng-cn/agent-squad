package com.agentsquad.squad.v2.agent.tracing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Langfuse 可观测字段序列化
 * <p>
 * 默认只输出工具名称、状态和计量；明确开启正文采集时，才输出经过限额的用户与模型公开文本。
 * 工具参数、结果、完整 Prompt、模型内部推理以及模型配置对象不进入追踪系统。
 */
public class LangfuseTraceSerializer {
    private static final int MAX_MESSAGES = 12;
    private static final int MAX_TOOLS = 20;

    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean captureContent;
    private final int maxChars;

    public LangfuseTraceSerializer(boolean captureContent, int maxChars) {
        this.captureContent = captureContent;
        this.maxChars = maxChars;
    }

    /**
     * 只取最近若干条消息，正文限额；工具调用和结果永不展开原始参数与返回值。
     */
    public String messages(List<Msg> messages) {
        if (!captureContent || messages == null) {
            return null;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int start = Math.max(0, messages.size() - MAX_MESSAGES);
        for (int index = start; index < messages.size(); index++) {
            Msg message = messages.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("text", previewText(message));
            item.put("tool_calls", describeTools(message.getContentBlocks(ToolUseBlock.class)));

            List<Map<String, Object>> toolResults = new ArrayList<>();
            List<ToolResultBlock> blocks = message.getContentBlocks(ToolResultBlock.class);
            for (int blockIndex = 0; blockIndex < Math.min(blocks.size(), MAX_TOOLS); blockIndex++) {
                ToolResultBlock block = blocks.get(blockIndex);
                Map<String, Object> toolResult = new LinkedHashMap<>();
                toolResult.put("name", block.getName());
                toolResult.put("block_count", block.getOutput() == null ? 0 : block.getOutput().size());
                toolResults.add(toolResult);
            }
            item.put("tool_results", toolResults);
            result.add(item);
        }
        return json(result);
    }

    /**
     * 工具名册用于展示模型选择了什么，不保留模型吐出的敏感参数。
     */
    public String toolCalls(List<ToolUseBlock> calls) {
        return json(describeTools(calls));
    }

    /**
     * 只记录模型公开正文和工具名册，模型推理文本在任何模式下都不导出。
     */
    public String modelOutput(CharSequence text, List<Map<String, Object>> calls) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (captureContent) {
            result.put("text", limit(text));
        }
        result.put("tool_calls", calls);
        return json(result);
    }

    /**
     * 仅采集普通生成参数，排除可能携带 API Key、请求头和服务器地址的扩展配置。
     */
    public String modelParameters(GenerateOptions options) {
        if (options == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("temperature", options.getTemperature());
        result.put("top_p", options.getTopP());
        result.put("max_tokens", options.getMaxTokens());
        return json(result);
    }

    /**
     * Langfuse 的细分用量字段，不将缓存 token 额外加进总输入，避免重复计费。
     */
    public String usageDetails(ChatUsage usage) {
        if (usage == null) {
            return null;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("input", usage.getInputTokens());
        details.put("output", usage.getOutputTokens());
        details.put("input_cached_tokens", usage.getCachedTokens());
        return json(details);
    }

    public String limit(CharSequence value) {
        if (value == null) {
            return "";
        }
        int size = Math.min(value.length(), maxChars / 2);
        String prefix = value.subSequence(0, size).toString();
        return size < value.length() ? prefix + "…" : prefix;
    }

    /**
     * 序列化失败只丢观测属性，不能让 Agent 执行失败。
     */
    public String json(Object value) {
        try {
            String encoded = mapper.writeValueAsString(value);
            if (encoded.length() <= maxChars) {
                return encoded;
            }
            return mapper.writeValueAsString(Map.of("truncated", true, "characters", encoded.length()));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private List<Map<String, Object>> describeTools(List<ToolUseBlock> calls) {
        List<Map<String, Object>> described = new ArrayList<>();
        if (calls == null) {
            return described;
        }
        for (int index = 0; index < Math.min(calls.size(), MAX_TOOLS); index++) {
            ToolUseBlock call = calls.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", call.getId());
            item.put("name", call.getName());
            item.put("argument_count", call.getInput() == null ? 0 : call.getInput().size());
            described.add(item);
        }
        return described;
    }

    /**
     * System Prompt 和工具消息不输出正文；用户与助手正文只按文本块提取有限前缀。
     */
    private String previewText(Msg message) {
        if (message.getRole() != MsgRole.USER && message.getRole() != MsgRole.ASSISTANT) {
            return "<omitted>";
        }
        StringBuilder preview = new StringBuilder();
        int remaining = maxChars / 2;
        for (TextBlock block : message.getContentBlocks(TextBlock.class)) {
            String text = block.getText();
            if (text == null || remaining == 0) {
                continue;
            }
            int size = Math.min(remaining, text.length());
            preview.append(text, 0, size);
            remaining -= size;
        }
        return limit(preview);
    }
}
