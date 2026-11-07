package com.agentsquad.squad.common.observability;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 公共追踪工具。
 * <p>
 * 统一创建业务步骤 Span、过滤可记录字段、归类错误类型，并把单条结构化日志限制在 4 KiB 以内。
 * 业务 Service 只依赖本组件，不直接操作第三方追踪 SDK。
 */
@Slf4j
@Component
public class ObservationSupport {
    /** 日志和追踪只保留异常类型与有限栈帧，避免上传供应商正文或业务入参。 */
    public static Throwable safeDiagnostic(Throwable failure) {
        var diagnostic = new IllegalStateException(failure.getClass().getName());
        var frames = failure.getStackTrace();
        diagnostic.setStackTrace(java.util.Arrays.copyOf(frames, Math.min(24, frames.length)));
        return diagnostic;
    }

    public static final int MAX_LOG_FIELD_BYTES = 4096;
    private static final int MAX_VALUE_CHARS = 512;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "requestId", "traceId", "spanId", "scopeId", "executionId", "taskId", "commandId",
            "jobId", "bindingId", "runId", "turnId", "messageId", "conversationId", "agentId",
            "step", "phase", "action", "method", "path", "statusCode", "attempt", "sequence",
            "status", "outcome", "nativeStatus", "errorCode", "errorType", "reason", "durationMs",
            "delayMs", "duplicate", "model", "provider", "toolName", "resultType", "inputTokens",
            "outputTokens", "tokenCount", "costMicrounits", "budget", "event", "truncated");
    private static final Set<String> CORE_FIELDS = Set.of(
            "requestId", "traceId", "spanId", "executionId", "taskId", "commandId",
            "step", "outcome", "errorCode", "truncated");
    private static final List<String> DROP_ORDER = List.of(
            "budget", "inputTokens", "outputTokens", "resultType", "model", "toolName",
            "delayMs", "durationMs", "duplicate", "attempt", "sequence", "nativeStatus",
            "errorType", "reason", "status", "method", "path", "statusCode", "action", "phase",
            "agentId", "conversationId", "messageId", "turnId", "runId", "bindingId", "jobId", "scopeId");

    @Resource
    private ObservationRegistry observationRegistry;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 在固定步骤名下执行一次业务动作，自动记录耗时、标签和异常类型。
     */
    public <T> T observe(String name, Map<String, String> tags, Supplier<T> action) {
        Observation observation = Observation.createNotStarted(name, observationRegistry)
                .lowCardinalityKeyValues(toKeyValues(tags));
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            return action.get();
        } catch (RuntimeException exception) {
            observation.error(safeDiagnostic(exception));
            log.warn("追踪步骤执行失败 step={} errorType={}", name, classify(exception));
            throw exception;
        } finally {
            observation.stop();
        }
    }

    /**
     * 无返回值版本，便于包裹已有的 void 业务步骤。
     */
    public void observe(String name, Map<String, String> tags, Runnable action) {
        observe(name, tags, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 过滤并截断结构化日志字段，保证单条记录不超过 4 KiB。
     */
    public Map<String, Object> logFields(Map<String, Object> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() == null || !ALLOWED_FIELDS.contains(entry.getKey())) {
                continue;
            }
            result.put(entry.getKey(), normalize(entry.getValue()));
        }
        if (serializedBytes(result) <= MAX_LOG_FIELD_BYTES) {
            return result;
        }
        for (String key : DROP_ORDER) {
            if (serializedBytes(result) <= MAX_LOG_FIELD_BYTES) {
                break;
            }
            if (result.containsKey(key) && !CORE_FIELDS.contains(key)) {
                result.remove(key);
            }
        }
        result.put("truncated", true);
        return result;
    }

    /**
     * 把异常归类为有限集合，日志只记录分类结果，不记录模型或框架异常原文。
     */
    public String classify(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return "TIMEOUT";
        }
        if (throwable instanceof IOException) {
            return "IO";
        }
        if (throwable instanceof RuntimeBusinessException) {
            return "BUSINESS";
        }
        if (throwable instanceof IllegalArgumentException) {
            return "VALIDATION";
        }
        if (throwable instanceof IllegalStateException) {
            return "STATE";
        }
        return "UNKNOWN";
    }

    private KeyValues toKeyValues(Map<String, String> tags) {
        List<KeyValue> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && ALLOWED_FIELDS.contains(entry.getKey())) {
                values.add(KeyValue.of(entry.getKey(), entry.getValue()));
            }
        }
        return KeyValues.of(values);
    }

    private Object normalize(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = String.valueOf(value);
        return text.length() <= MAX_VALUE_CHARS ? text : text.substring(0, MAX_VALUE_CHARS) + "…";
    }

    private int serializedBytes(Map<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
