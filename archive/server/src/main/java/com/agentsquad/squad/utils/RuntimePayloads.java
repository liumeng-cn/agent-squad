package com.agentsquad.squad.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行载荷的通用 JSON 转换。
 * <p>
 * 统一序列化与 MySQL/H2 JSON 读取差异，不处理业务字段或权限规则；业务 ID 在载荷中一律输出十进制字符串。
 */
public final class RuntimePayloads {

    private RuntimePayloads() {
    }

    public static String toJson(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(normalizeIds(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("运行载荷无法序列化", exception);
        }
    }

    public static JsonNode readJson(ObjectMapper mapper, String raw) throws JsonProcessingException {
        JsonNode first = mapper.readTree(raw);
        return first.isTextual() ? mapper.readTree(first.asText()) : first;
    }

    /**
     * 递归把 ID 键上的数字转换为字符串，避免 Long 业务 ID 以数字形态进入持久载荷。
     */
    private static Object normalizeIds(Object value) {
        // JsonNode 本身实现 Iterable，必须先按 JSON 节点处理，否则对象会被退化成值数组。
        if (value instanceof JsonNode node) {
            return normalizeNode(node);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), normalizeEntry(entry.getKey(), entry.getValue()));
            }
            return copy;
        }
        if (value instanceof Iterable<?> items) {
            List<Object> copy = new ArrayList<>();
            for (Object item : items) {
                copy.add(normalizeIds(item));
            }
            return copy;
        }
        return value;
    }

    private static Object normalizeEntry(Object key, Object value) {
        if (key instanceof String name && isIdKey(name)) {
            if (value instanceof Long || value instanceof Integer) {
                return value.toString();
            }
            if (value instanceof Iterable<?> items) {
                List<Object> ids = new ArrayList<>();
                for (Object item : items) {
                    ids.add(item == null ? null : item.toString());
                }
                return ids;
            }
        }
        return normalizeIds(value);
    }

    private static JsonNode normalizeNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry ->
                    copy.set(entry.getKey(), normalizeNodeEntry(entry.getKey(), entry.getValue())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> copy.add(normalizeNode(item)));
            return copy;
        }
        return node;
    }

    private static JsonNode normalizeNodeEntry(String key, JsonNode value) {
        if (isIdKey(key) && value.isIntegralNumber()) {
            return TextNode.valueOf(value.asText());
        }
        return normalizeNode(value);
    }

    private static boolean isIdKey(String key) {
        return key.endsWith("Id") || key.endsWith("Ids");
    }
}
