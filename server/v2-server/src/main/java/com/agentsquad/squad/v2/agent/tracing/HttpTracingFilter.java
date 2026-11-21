package com.agentsquad.squad.v2.agent.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Squad V2 Servlet HTTP 入口追踪
 * <p>
 * 只记录一次 HTTP 接收与处理，不把长时间后台 AgentSession 执行误算成 HTTP 请求耗时。
 * 已使用 OTel Java Agent 的环境应关闭本过滤器，避免重复 HTTP Span。
 */
public class HttpTracingFilter extends OncePerRequestFilter {
    public static final String SPAN_ATTRIBUTE = HttpTracingFilter.class.getName() + ".span";
    public static final String OUTPUT_CAPTURED_ATTRIBUTE = HttpTracingFilter.class.getName() + ".outputCaptured";

    @Resource
    private OpenTelemetrySdk observationSdk;

    @Resource
    private LangfuseTraceSerializer httpTraceSerializer;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/v2/chat/messages".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Context parent = W3CTraceContextPropagator.getInstance()
                .extract(Context.current(), request, ServletTraceHeaderGetter.INSTANCE);
        Span span = observationSdk.getTracer("com.agentsquad.squad.v2.http")
                .spanBuilder("HTTP " + request.getMethod())
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.request.method", request.getMethod())
                .startSpan();

        request.setAttribute(SPAN_ATTRIBUTE, span);
        recordInput(request, span);
        try (Scope ignored = span.makeCurrent()) {
            filterChain.doFilter(request, response);
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (route instanceof String routeName) {
                span.setAttribute("http.route", routeName);
            }
            span.setAttribute("http.response.status_code", (long) response.getStatus());
            if (response.getStatus() >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
        } catch (IOException | ServletException | RuntimeException error) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR);
            throw error;
        } finally {
            recordResponseSummary(request, response, span);
            span.end();
        }
    }

    private void recordInput(HttpServletRequest request, Span span) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("method", request.getMethod());
        input.put("path", request.getRequestURI());
        input.put("query", request.getParameterMap());
        String payload = httpTraceSerializer.httpPayload(input);
        if (payload != null) {
            span.setAttribute("langfuse.observation.input", payload);
        }
    }

    /**
     * JSON 正文由 MVC 采集器记录；未生成正文时保留状态摘要，不虚构响应内容。
     */
    private void recordResponseSummary(HttpServletRequest request, HttpServletResponse response, Span span) {
        if (Boolean.TRUE.equals(request.getAttribute(OUTPUT_CAPTURED_ATTRIBUTE))) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", response.getStatus());
        output.put("bodyCaptured", false);
        String payload = httpTraceSerializer.httpPayload(output);
        if (payload != null) {
            span.setAttribute("langfuse.observation.output", payload);
        }
    }
}
