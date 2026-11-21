package com.agentsquad.squad.v2.agent.tracing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Langfuse 可观测字段序列化
 * <p>
 * 默认只输出工具名称、状态和计量；明确开启正文采集时，才输出经过限额的用户与模型公开文本。
 * 系统提示和工具定义保留结构标识，工具参数与结果脱敏限长；不导出模型内部推理和模型配置对象。
 */
public class LangfuseTraceSerializer {
    private static final int MAX_MESSAGES = 12;
    private static final int MAX_TOOLS = 20;
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)([\"']?(?:password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|cookie)[\"']?\\s*[:=]\\s*)"
                    + "(\"[^\"]*\"|'[^']*'|[^\\s,;}]+)");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectMapper fingerprintMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final boolean captureContent;
    private final int maxChars;
    private final int maxToolChars;

    public LangfuseTraceSerializer(boolean captureContent, int maxChars) {
        this(captureContent, maxChars, Math.min(maxChars, 2048));
    }

    public LangfuseTraceSerializer(boolean captureContent, int maxChars, int maxToolChars) {
        this.captureContent = captureContent;
        this.maxChars = maxChars;
        this.maxToolChars = Math.min(maxChars, maxToolChars);
    }

    /**
     * 系统提示保留标识和结构，最近交互保留正文；工具仅展示调用和结果摘要。
     */
    public String messages(List<Msg> messages) {
        if (!captureContent || messages == null) {
            return null;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int start = recentInteractionStart(messages);
        int systemCount = 0;
        for (int index = 0; index < messages.size(); index++) {
            Msg message = messages.get(index);
            if (message.getRole() == MsgRole.SYSTEM) {
                if (systemCount < 4) {
                    result.add(systemPlaceholder(message));
                    systemCount++;
                }
                continue;
            }
            if (index < start) {
                continue;
            }
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
                toolResult.put("content", captureContent ? "<non-text blocks omitted>" : "<tool result omitted>");
                StringBuilder text = new StringBuilder();
                if (block.getOutput() != null) {
                    for (var output : block.getOutput()) {
                        if (output instanceof TextBlock outputText && outputText.getText() != null) {
                            int remaining = maxToolChars - text.length();
                            if (remaining <= 0) {
                                break;
                            }
                            String value = outputText.getText();
                            text.append(value, 0, Math.min(remaining, value.length()));
                        }
                    }
                }
                toolResult.put("text", toolResultPreview(text));
                toolResults.add(toolResult);
            }
            item.put("tool_results", toolResults);
            result.add(item);
        }
        return json(result);
    }

    private Map<String, Object> systemPlaceholder(Msg message) {
        StringBuilder text = new StringBuilder();
        for (TextBlock block : message.getContentBlocks(TextBlock.class)) {
            if (block.getText() != null) {
                text.append(block.getText());
            }
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", message.getRole());
        item.put("reference", "system:" + fingerprint(text.toString()));
        item.put("characters", text.length());
        item.put("text", "<system prompt omitted>");
        return item;
    }

    /**
     * 普通工具与 MCP 工具统一保留模型实际可见的名称、定义标识和参数结构。
     * 描述、默认值和枚举正文不展开，也不推测 SDK 未提供的 MCP 服务器来源。
     */
    public String toolDefinitions(List<ToolSchema> tools) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        if (tools == null) {
            return json(definitions);
        }
        for (int index = 0; index < Math.min(tools.size(), MAX_TOOLS); index++) {
            ToolSchema tool = tools.get(index);
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("name", tool.getName());
            definition.put("reference", "tool:" + fingerprint(tool));
            definition.put("description", "<tool description omitted>");
            definition.put("parameters", schemaStructure(tool.getParameters(), 0));
            definitions.add(definition);
        }
        return json(definitions);
    }

    private Map<String, Object> schemaStructure(Map<?, ?> schema, int depth) {
        Map<String, Object> structure = new LinkedHashMap<>();
        if (schema == null || depth >= 4) {
            structure.put("content", "<schema omitted>");
            return structure;
        }
        Object type = schema.get("type");
        if (type instanceof String) {
            structure.put("type", type);
        }
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : properties.entrySet()) {
                if (fields.size() == MAX_TOOLS) {
                    break;
                }
                if (field.getKey() instanceof String name && field.getValue() instanceof Map<?, ?> fieldSchema) {
                    fields.put(name, schemaStructure(fieldSchema, depth + 1));
                }
            }
            structure.put("properties", fields);
        }
        if (schema.get("items") instanceof Map<?, ?> items) {
            structure.put("items", schemaStructure(items, depth + 1));
        }
        if (schema.get("required") instanceof List<?> required) {
            structure.put("required", required.stream().filter(String.class::isInstance).limit(MAX_TOOLS).toList());
        }
        if (schema.get("enum") instanceof List<?> values) {
            structure.put("enum_count", values.size());
        }
        return structure;
    }

    private String fingerprint(Object value) {
        try {
            byte[] encoded = value instanceof String text ? text.getBytes(StandardCharsets.UTF_8)
                    : fingerprintMapper.writeValueAsBytes(value);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }

    private int recentInteractionStart(List<Msg> messages) {
        int count = 0;
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index).getRole() != MsgRole.SYSTEM) {
                count++;
                if (count == MAX_MESSAGES) {
                    return index;
                }
            }
        }
        return 0;
    }

    /**
     * 在 Preview 中同时展示交互消息和工具结构，避免用户必须到元数据查找工具定义。
     */
    public String modelInput(List<Msg> messages, List<ToolSchema> tools) {
        if (!captureContent) {
            return null;
        }
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            String interaction = messages(messages);
            input.put("messages", interaction == null ? List.of() : mapper.readTree(interaction));
            input.put("tools", mapper.readTree(toolDefinitions(tools)));
            input.put("tool_count", tools == null ? 0 : tools.size());
            return json(input);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    /**
     * 工具名册与参数结构始终保留；明确开启采集时附加脱敏且限长的参数值。
     */
    public String toolCalls(List<ToolUseBlock> calls) {
        return boundedJson(describeTools(calls), maxToolChars);
    }

    public String toolResultPreview(CharSequence value) {
        if (!captureContent || value == null) {
            return null;
        }
        String redacted = BEARER_TOKEN.matcher(value).replaceAll("Bearer <redacted>");
        redacted = SENSITIVE_ASSIGNMENT.matcher(redacted).replaceAll("$1<redacted>");
        return limitedPrefix(redacted, maxToolChars / 2);
    }

    public String toolResults(Object results) {
        return boundedJson(results, maxToolChars);
    }

    /**
     * 展示 HTTP 出入参
     *
     * 只转换已经解析的协议对象，复用敏感字段脱敏和属性限额，不读取 Servlet 数据流。
     */
    public String httpPayload(Object value) {
        if (!captureContent) {
            return null;
        }
        try {
            Object payload = mapper.convertValue(value, Object.class);
            return json(sanitizedValue(payload, 0, 12));
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
        return limitedPrefix(value, maxChars / 2);
    }

    private String limitedPrefix(CharSequence value, int limit) {
        if (value == null) {
            return "";
        }
        int size = Math.min(value.length(), limit);
        if (size > 0 && size < value.length() && Character.isHighSurrogate(value.charAt(size - 1))) {
            size--;
        }
        String prefix = value.subSequence(0, size).toString();
        return size < value.length() ? prefix + "…" : prefix;
    }

    /**
     * 序列化失败只丢观测属性，不能让 Agent 执行失败。
     */
    public String json(Object value) {
        return boundedJson(value, maxChars);
    }

    private String boundedJson(Object value, int limit) {
        try {
            String encoded = mapper.writeValueAsString(value);
            if (encoded.length() <= limit) {
                return encoded;
            }
            return truncatedPreview(encoded, limit);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 超限时保留可读前缀及原长度；按转义后的 JSON 长度限额，避免再次丢掉正文。
     */
    private String truncatedPreview(String encoded, int limit) throws JsonProcessingException {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("truncated", true);
        preview.put("characters", encoded.length());
        int lower = 0;
        int upper = Math.min(encoded.length(), limit);
        String result = null;
        while (lower <= upper) {
            int length = (lower + upper) / 2;
            int end = length;
            if (end > 0 && Character.isHighSurrogate(encoded.charAt(end - 1))) {
                end--;
            }
            preview.put("preview", encoded.substring(0, end));
            String candidate = mapper.writeValueAsString(preview);
            if (candidate.length() <= limit) {
                result = candidate;
                lower = length + 1;
            } else {
                upper = length - 1;
            }
        }
        return result;
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
            item.put("arguments", argumentStructure(call.getInput(), 0));
            if (captureContent) {
                item.put("values", argumentValues(call.getInput(), 0));
            }
            described.add(item);
        }
        return described;
    }

    private Map<String, Object> argumentValues(Map<?, ?> arguments, int depth) {
        return argumentValues(arguments, depth, 4);
    }

    private Map<String, Object> argumentValues(Map<?, ?> arguments, int depth, int maxDepth) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (arguments == null || depth >= maxDepth) {
            return values;
        }
        for (Map.Entry<?, ?> entry : arguments.entrySet()) {
            if (values.size() == MAX_TOOLS) {
                break;
            }
            String name = String.valueOf(entry.getKey());
            if (sensitiveField(name)) {
                values.put("<redacted-field-" + values.size() + ">", "<redacted>");
            } else {
                values.put(name, sanitizedValue(entry.getValue(), depth + 1, maxDepth));
            }
        }
        return values;
    }

    private Object sanitizedValue(Object value, int depth, int maxDepth) {
        if (depth >= maxDepth) {
            return "<nested content omitted>";
        }
        if (value instanceof Map<?, ?> nested) {
            return argumentValues(nested, depth, maxDepth);
        }
        if (value instanceof List<?> list) {
            List<Object> items = new ArrayList<>();
            for (int index = 0; index < Math.min(list.size(), MAX_TOOLS); index++) {
                items.add(sanitizedValue(list.get(index), depth + 1, maxDepth));
            }
            return items;
        }
        if (value instanceof Number || value instanceof Boolean || value == null) {
            return value;
        }
        return toolResultPreview(String.valueOf(value));
    }

    private boolean sensitiveField(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("passwd") || normalized.contains("secret")
                || normalized.contains("token") || normalized.contains("cookie") || normalized.contains("authorization")
                || normalized.contains("key") || normalized.contains("thinking") || normalized.contains("reasoning")
                || normalized.contains("prompt");
    }

    /**
     * 调用参数只保留字段与类型；敏感字段名也替换，所有实际参数值均省略。
     */
    private Map<String, Object> argumentStructure(Map<?, ?> arguments, int depth) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (arguments == null || depth >= 4) {
            return fields;
        }
        for (Map.Entry<?, ?> entry : arguments.entrySet()) {
            if (fields.size() == MAX_TOOLS) {
                break;
            }
            String name = String.valueOf(entry.getKey());
            if (sensitiveField(name)) {
                name = "<redacted-field-" + fields.size() + ">";
            }
            Object value = entry.getValue();
            Map<String, Object> shape = new LinkedHashMap<>();
            if (value instanceof Map<?, ?> nested) {
                shape.put("type", "object");
                shape.put("properties", argumentStructure(nested, depth + 1));
            } else if (value instanceof List<?> list) {
                shape.put("type", "array");
                shape.put("count", list.size());
            } else if (value instanceof Number) {
                shape.put("type", "number");
            } else if (value instanceof Boolean) {
                shape.put("type", "boolean");
            } else {
                shape.put("type", value == null ? "null" : "string");
            }
            shape.put("content", "<omitted>");
            fields.put(name, shape);
        }
        return fields;
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
