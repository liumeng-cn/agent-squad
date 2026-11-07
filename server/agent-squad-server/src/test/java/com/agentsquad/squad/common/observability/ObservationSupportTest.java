package com.agentsquad.squad.common.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公共追踪工具的字段过滤、大小上限与错误分类验证。
 */
class ObservationSupportTest {

    private ObservationSupport support() {
        ObservationSupport support = new ObservationSupport();
        ReflectionTestUtils.setField(support, "objectMapper", new ObjectMapper());
        return support;
    }

    @Test
    void dropsSensitiveAndUnknownFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("executionId", "execution-1");
        fields.put("prompt", "do not persist");
        fields.put("password", "secret-value");
        fields.put("cookie", "SQUAD_SESSION=secret");
        fields.put("requestBody", "raw payload");

        Map<String, Object> result = support().logFields(fields);

        assertEquals(Map.of("executionId", "execution-1"), result);
    }

    @Test
    void truncatesLongValueWithinSingleEntry() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("executionId", "execution-1");
        fields.put("reason", "x".repeat(600));

        Map<String, Object> result = support().logFields(fields);

        assertEquals("execution-1", result.get("executionId"));
        assertEquals(513, String.valueOf(result.get("reason")).length());
        assertTrue(serializedBytes(result) <= ObservationSupport.MAX_LOG_FIELD_BYTES);
    }

    @Test
    void marksTruncatedWhenAllowedFieldsExceedLimit() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("executionId", "execution-1");
        for (String key : new String[]{"scopeId", "taskId", "commandId", "bindingId", "runId",
                "turnId", "messageId", "conversationId", "agentId", "path", "method"}) {
            fields.put(key, "z".repeat(500));
        }

        Map<String, Object> result = support().logFields(fields);

        assertTrue(serializedBytes(result) <= ObservationSupport.MAX_LOG_FIELD_BYTES);
        assertEquals(true, result.get("truncated"));
        assertEquals("execution-1", result.get("executionId"));
        assertFalse(result.containsKey("path"));
    }

    @Test
    void emitsFieldsInCanonicalOrderRegardlessOfInputMap() {
        Map<String, Object> fields = Map.of(
                "scopeId", "scope-1", "executionId", "execution-1", "modelId", "default",
                "reasoningMode", "OFF", "outcome", "FAILED", "commandId", "command-1",
                "preview", "你好", "reason", "模型未开通");

        Map<String, Object> result = support().logFields(fields);

        assertEquals(java.util.List.of("outcome", "reason", "preview", "modelId", "reasoningMode",
                "executionId", "commandId", "scopeId"), java.util.List.copyOf(result.keySet()));
    }

    @Test
    void classifiesKnownErrorFamilies() {
        ObservationSupport support = support();

        assertEquals("TIMEOUT", support.classify(new TimeoutException()));
        assertEquals("IO", support.classify(new IOException("disk")));
        assertEquals("VALIDATION", support.classify(new IllegalArgumentException("bad")));
        assertEquals("STATE", support.classify(new IllegalStateException("state")));
        assertEquals("UNKNOWN", support.classify(new RuntimeException("other")));
    }

    private int serializedBytes(Map<String, Object> value) {
        try {
            return new ObjectMapper().writeValueAsString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
