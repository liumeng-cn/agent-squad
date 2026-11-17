package com.agentsquad.squad.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务端请求关联过滤器的请求标识复用与 MDC 清理验证。
 */
class RequestCorrelationFilterTest {

    private static final String VALID_REQUEST_ID = "11111111-1111-1111-1111-111111111111";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesValidRequestIdAndClearsMdcAfterRequest() throws Exception {
        RequestCorrelationFilter filter = filterWithSpan("trace-1", "span-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/conversations");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, VALID_REQUEST_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> spanId = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            requestId.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
            traceId.set(MDC.get(RequestCorrelationFilter.TRACE_ID_MDC_KEY));
            spanId.set(MDC.get(RequestCorrelationFilter.SPAN_ID_MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertEquals(VALID_REQUEST_ID, requestId.get());
        assertEquals("trace-1", traceId.get());
        assertEquals("span-1", spanId.get());
        assertEquals(VALID_REQUEST_ID, response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
        assertNull(MDC.get(RequestCorrelationFilter.TRACE_ID_MDC_KEY));
        assertNull(MDC.get(RequestCorrelationFilter.SPAN_ID_MDC_KEY));
    }

    @Test
    void replacesForgedRequestId() throws Exception {
        RequestCorrelationFilter filter = filterWithSpan(null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/conversations");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "forged-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestId = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                requestId.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertNotEquals("forged-id", requestId.get());
        assertTrue(requestId.get().matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        assertEquals(requestId.get(), response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }

    @Test
    void clearsMdcWhenChainThrows() {
        RequestCorrelationFilter filter = filterWithSpan("trace-1", "span-1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/messages");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception expected) {
            // 过滤器必须清理上下文后继续抛出原异常。
        }

        assertNull(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
        assertNull(MDC.get(RequestCorrelationFilter.TRACE_ID_MDC_KEY));
        assertNull(MDC.get(RequestCorrelationFilter.SPAN_ID_MDC_KEY));
    }

    private RequestCorrelationFilter filterWithSpan(String traceId, String spanId) {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        Tracer tracer = mock(Tracer.class);
        if (traceId == null) {
            when(tracer.currentSpan()).thenReturn(null);
        } else {
            Span span = mock(Span.class);
            TraceContext context = mock(TraceContext.class);
            when(context.traceId()).thenReturn(traceId);
            when(context.spanId()).thenReturn(spanId);
            when(span.context()).thenReturn(context);
            when(tracer.currentSpan()).thenReturn(span);
        }
        ReflectionTestUtils.setField(filter, "tracer", tracer);
        return filter;
    }
}
