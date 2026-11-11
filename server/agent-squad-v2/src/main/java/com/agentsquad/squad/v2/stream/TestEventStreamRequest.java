package com.agentsquad.squad.v2.stream;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 本地 SSE 事件测试请求。 */
public record TestEventStreamRequest(
        String scenario,
        @Min(0) @Max(2000) Integer intervalMillis
) {
    public String normalizedScenario() {
        return scenario == null || scenario.isBlank()
                ? "parallel" : scenario.trim().toLowerCase();
    }

    public int normalizedIntervalMillis() {
        return intervalMillis == null ? 300 : intervalMillis;
    }
}
