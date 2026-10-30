package com.agentsquad.squad.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 持久 JSON 的规范化摘要工具。
 *
 * MySQL JSON 列可能重排对象键；摘要在写入前和读取后都先按键排序，再计算内容哈希。
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String sha256(ObjectMapper mapper, String json) {
        try {
            JsonNode node = RuntimePayloads.readJson(mapper, json);
            return ContentDigests.sha256(RuntimePayloads.toJson(mapper, normalize(node)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("持久 JSON 无法规范化", exception);
        }
    }

    private static Object normalize(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), normalize(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(normalize(value)));
            return values;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return null;
    }
}
