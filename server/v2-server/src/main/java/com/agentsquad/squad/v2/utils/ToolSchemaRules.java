package com.agentsquad.squad.v2.utils;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具参数结构校验
 * <p>
 * 工具参数使用 JSON Schema 描述，顶层必须是 object；限制参数数量、名称和总大小，避免模型请求过大或名称无法引用。
 */
public final class ToolSchemaRules {
    private static final int MAX_PROPERTIES = 64;

    private static final int MAX_SCHEMA_BYTES = 32 * 1024;

    private static final String PROPERTY_NAME = "[A-Za-z_][A-Za-z0-9_]{0,63}";

    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolSchemaRules() {
    }

    /**
     * 校验参数结构并返回参数名
     * <p>
     * 校验失败返回参数错误；required 中的名称必须已在 properties 中定义。
     */
    public static Set<String> validate(Map<String, Object> schema) {
        if (!"object".equals(schema.get("type"))) {
            throw invalid("参数结构顶层 type 必须为 object");
        }
        if (sizeOf(schema) > MAX_SCHEMA_BYTES) {
            throw invalid("参数结构不能超过 32KB");
        }

        Object propertiesValue = schema.getOrDefault("properties", Map.of());
        if (!(propertiesValue instanceof Map<?, ?> properties)) {
            throw invalid("参数结构的 properties 必须是对象");
        }
        if (properties.size() > MAX_PROPERTIES) {
            throw invalid("参数不能超过 " + MAX_PROPERTIES + " 个");
        }

        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!name.matches(PROPERTY_NAME)) {
                throw invalid("参数名 " + name + " 只能包含字母、数字和下划线，且不能以数字开头");
            }
            if (!(entry.getValue() instanceof Map<?, ?>)) {
                throw invalid("参数 " + name + " 的定义必须是对象");
            }
            names.add(name);
        }

        Object requiredValue = schema.getOrDefault("required", List.of());
        if (!(requiredValue instanceof List<?> required)) {
            throw invalid("参数结构的 required 必须是数组");
        }
        for (Object item : required) {
            if (!names.contains(String.valueOf(item))) {
                throw invalid("必填参数 " + item + " 未在 properties 中定义");
            }
        }
        return names;
    }

    private static int sizeOf(Map<String, Object> schema) {
        try {
            return JSON.writeValueAsBytes(schema).length;
        } catch (JsonProcessingException unreadable) {
            throw invalid("参数结构无法序列化为 JSON");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ApiCode.BAD_REQUEST, message);
    }
}
