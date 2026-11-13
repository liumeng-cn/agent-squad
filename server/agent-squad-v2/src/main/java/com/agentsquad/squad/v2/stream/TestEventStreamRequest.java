package com.agentsquad.squad.v2.stream;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 本地 SSE 事件测试请求。
 */
public record TestEventStreamRequest(
        @Min(0) @Max(2000) Integer intervalMillis
) {
    public int normalizedIntervalMillis() {
        return intervalMillis == null ? 300 : intervalMillis;
    }
}
