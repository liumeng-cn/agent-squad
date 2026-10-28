package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.common.util.RuntimePayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作参数的保守展示摘要。
 * <p>
 * 仅公开固定白名单字段的类型和长度，不回显任意用户文本、工具结果或嵌入凭据。
 */
@Component
public class OperationPreview {
    private static final List<String> FIELDS = List.of("title", "description", "query", "environment", "projectKey");

    @Resource
    private ObjectMapper objectMapper;

    public Map<String, Object> arguments(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            JsonNode value = RuntimePayloads.readJson(objectMapper, json);
            for (String field : FIELDS) {
                JsonNode item = value.get(field);
                if (item != null) {
                    result.put(field, item.isTextual() ? "文本（" + item.textValue().length() + " 字符，内容已隐藏）"
                            : "结构化值（内容已隐藏）");
                }
            }
        } catch (Exception exception) {
            return Map.of();
        }
        return result;
    }
}
