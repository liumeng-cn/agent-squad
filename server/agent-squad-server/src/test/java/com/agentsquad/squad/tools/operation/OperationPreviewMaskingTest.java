package com.agentsquad.squad.tools.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作参数日志脱敏回归验证（D6 3.2）。
 * <p>
 * 验证 OperationPreview 只公开固定白名单字段的类型和长度，隐藏实际内容，
 * 不回显任意用户文本、工具结果或嵌入凭据（apiKey/password/secret/token 等）。
 */
class OperationPreviewMaskingTest {

    private OperationPreview preview;

    @BeforeEach
    void setUp() {
        preview = new OperationPreview();
        ReflectionTestUtils.setField(preview, "objectMapper", new ObjectMapper());
    }

    @Test
    void exposesOnlyWhitelistFieldsAndHidesContent() {
        String json = "{\"title\":\"我的项目\",\"description\":\"描述\",\"query\":\"搜索词\","
                + "\"environment\":\"prod\",\"projectKey\":\"key-1\",\"apiKey\":\"sk-secret-1234567890\","
                + "\"password\":\"hunter2\",\"secret\":\"topsecret\",\"token\":\"bearer-xyz\"}";
        Map<String, Object> result = preview.arguments(json);

        assertThat(result).containsOnlyKeys("title", "description", "query", "environment", "projectKey");
        assertThat(result).doesNotContainKeys("apiKey", "password", "secret", "token");
        assertThat(result.get("title").toString()).contains("内容已隐藏").doesNotContain("我的项目");
        assertThat(result.get("description").toString()).contains("内容已隐藏").doesNotContain("描述");
        assertThat(result.get("query").toString()).contains("内容已隐藏").doesNotContain("搜索词");
    }

    @Test
    void hidesStructuredAndNumericValues() {
        String json = "{\"environment\":{\"region\":\"cn\"},\"query\":42,\"title\":\"x\"}";
        Map<String, Object> result = preview.arguments(json);
        assertThat(result).containsOnlyKeys("environment", "query", "title");
        assertThat(result.get("environment").toString()).contains("结构化值").contains("内容已隐藏");
        assertThat(result.get("query").toString()).contains("结构化值").contains("内容已隐藏");
    }

    @Test
    void doesNotEchoArbitraryUserTextOrCredentials() {
        String json = "{\"title\":\"secret-key-value\",\"apiKey\":\"sk-abcdef1234567890abcdef1234567890\"}";
        Map<String, Object> result = preview.arguments(json);
        assertThat(result).containsOnlyKeys("title");
        assertThat(result.get("title").toString()).doesNotContain("secret-key-value");
        assertThat(result.toString()).doesNotContain("sk-abcdef");
    }

    @Test
    void returnsEmptyOnInvalidJson() {
        assertThat(preview.arguments("not-json")).isEmpty();
        assertThat(preview.arguments("")).isEmpty();
    }

    @Test
    void whitelistFieldsCoverDocumentedSet() {
        var fields = (List<String>) ReflectionTestUtils.getField(preview, "FIELDS");
        assertThat(fields).containsExactly("title", "description", "query", "environment", "projectKey");
    }
}
