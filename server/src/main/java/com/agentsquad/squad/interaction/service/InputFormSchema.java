package com.agentsquad.squad.interaction.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 页面追问表单的受限 Schema 契约。
 * <p>
 * 仅支持最多 16 个平铺标量字段与有界枚举；缺省采用 answer 文本字段，拒绝引用和可执行表达式。
 */
@Component
public class InputFormSchema {
    private static final Set<String> ROOT_KEYS = Set.of("type", "properties", "required", "additionalProperties", "title", "description");

    private static final Set<String> FIELD_KEYS = Set.of("type", "title", "description", "enum", "minLength", "maxLength", "minimum", "maximum");

    @Resource
    private ObjectMapper objectMapper;

    public JsonNode normalize(JsonNode schema) {
        if (schema == null || schema.isNull() || (schema.isObject() && schema.size() == 0)
                || (schema.isObject() && schema.size() == 1 && "object".equals(schema.path("type").asText()))) {
            return objectMapper.valueToTree(Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of("answer", Map.of("type", "string", "title", "补充信息", "minLength", 1, "maxLength", 4000)),
                    "required", List.of("answer")));
        }
        if (!schema.isObject() || schema.toString().length() > 8192
                || !"object".equals(schema.path("type").asText()) || !schema.path("properties").isObject()
                || schema.path("properties").size() < 1 || schema.path("properties").size() > 16) {
            throw invalid();
        }
        schema.fieldNames().forEachRemaining(key -> {
            if (!ROOT_KEYS.contains(key)) {
                throw invalid();
            }
        });
        schema.path("properties").fields().forEachRemaining(field -> {
            JsonNode value = field.getValue();
            if (!field.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || !value.isObject()
                    || !Set.of("string", "number", "integer", "boolean").contains(value.path("type").asText())) {
                throw invalid();
            }
            value.fieldNames().forEachRemaining(key -> {
                if (!FIELD_KEYS.contains(key)) {
                    throw invalid();
                }
            });
            if (value.has("enum") && (!value.get("enum").isArray() || value.get("enum").isEmpty()
                    || value.get("enum").size() > 32)) {
                throw invalid();
            }
            for (JsonNode item : value.path("enum")) {
                if (!item.isValueNode() || item.isNull() || item.toString().length() > 200) {
                    throw invalid();
                }
            }
        });
        if (schema.has("required") && !schema.get("required").isArray()) {
            throw invalid();
        }
        for (JsonNode field : schema.path("required")) {
            if (!field.isTextual() || !schema.path("properties").has(field.textValue())) {
                throw invalid();
            }
        }
        JsonNode result = schema.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) result).put("additionalProperties", false);
        return result;
    }

    private RuntimeBusinessException invalid() {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "追问表单仅支持平铺的文本、数字、布尔值及有界枚举字段");
    }
}
