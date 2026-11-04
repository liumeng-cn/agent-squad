package com.agentsquad.squad.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP 测试读取统一响应包装中的 data 载荷。
 */
public final class ApiResultJson {

    private ApiResultJson() {
    }

    public static JsonNode data(ObjectMapper mapper, String body) throws JsonProcessingException {
        JsonNode root = mapper.readTree(body);
        JsonNode data = root.get("data");
        if (data == null) {
            throw new AssertionError("响应缺少 data 字段：" + body);
        }
        return data;
    }

    public static <T> T readData(ObjectMapper mapper, String body, Class<T> type) throws JsonProcessingException {
        return mapper.treeToValue(data(mapper, body), type);
    }
}
