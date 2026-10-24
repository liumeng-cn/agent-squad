package com.agentsquad.squad.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 运行载荷的通用 JSON 转换。
 * <p>
 * 统一序列化与 MySQL/H2 JSON 读取差异，不处理业务字段或权限规则。
 */
public final class RuntimePayloads {

    private RuntimePayloads() {
    }

    public static String toJson(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("运行载荷无法序列化", exception);
        }
    }

    public static JsonNode readJson(ObjectMapper mapper, String raw) throws JsonProcessingException {
        JsonNode first = mapper.readTree(raw);
        return first.isTextual() ? mapper.readTree(first.asText()) : first;
    }
}
