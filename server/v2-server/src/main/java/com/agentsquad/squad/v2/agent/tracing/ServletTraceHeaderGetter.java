package com.agentsquad.squad.v2.agent.tracing;

import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;

/**
 * Servlet 请求头的 W3C Trace Context 提取适配
 * <p>
 * 只读协议追踪字段，不复制 Cookie 或用户业务请求正文。
 */
final class ServletTraceHeaderGetter implements TextMapGetter<HttpServletRequest> {
    static final ServletTraceHeaderGetter INSTANCE = new ServletTraceHeaderGetter();

    private ServletTraceHeaderGetter() {
    }

    @Override
    public Iterable<String> keys(HttpServletRequest carrier) {
        return carrier == null ? Collections.emptyList() : Collections.list(carrier.getHeaderNames());
    }

    @Override
    public String get(HttpServletRequest carrier, String key) {
        return carrier == null ? null : carrier.getHeader(key);
    }
}
