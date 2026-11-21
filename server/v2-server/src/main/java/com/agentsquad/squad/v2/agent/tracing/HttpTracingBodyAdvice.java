package com.agentsquad.squad.v2.agent.tracing;

import io.opentelemetry.api.trace.Span;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天 HTTP 正文采集
 * <p>
 * 在 MVC 完成入参解析和返回值组装后补充 HTTP Span，不包装或修改协议对象。
 * 仅采集 JSON，SSE 和文件响应沿用原始写出方式，不读取或缓存 Servlet 数据流。
 */
@ControllerAdvice
@ConditionalOnProperty(prefix = "agentsquad.v2.observation", name = {"enabled", "http-enabled"}, havingValue = "true")
public class HttpTracingBodyAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {

    @Resource
    private LangfuseTraceSerializer httpTraceSerializer;

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return hasTracedRequest();
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!isJson(inputMessage.getHeaders().getContentType())) {
            return body;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("method", request.getMethod());
            input.put("path", request.getRequestURI());
            input.put("query", request.getParameterMap());
            input.put("body", body);
            writePayload(request, "langfuse.observation.input", input);
        }
        return body;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return hasTracedRequest();
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!isJson(selectedContentType) || !(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)) {
            return body;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", servletResponse.getServletResponse().getStatus());
        output.put("body", body);
        HttpServletRequest servlet = servletRequest.getServletRequest();
        if (writePayload(servlet, "langfuse.observation.output", output)) {
            servlet.setAttribute(HttpTracingFilter.OUTPUT_CAPTURED_ATTRIBUTE, true);
        }
        return body;
    }

    private boolean writePayload(HttpServletRequest request, String key, Object payload) {
        if (!(request.getAttribute(HttpTracingFilter.SPAN_ATTRIBUTE) instanceof Span span) || !span.isRecording()) {
            return false;
        }
        String content = httpTraceSerializer.httpPayload(payload);
        if (content == null) {
            return false;
        }
        span.setAttribute(key, content);
        return true;
    }

    private boolean hasTracedRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(HttpTracingFilter.SPAN_ATTRIBUTE) instanceof Span;
    }

    private boolean isJson(MediaType contentType) {
        return contentType != null && (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || contentType.getSubtype().endsWith("+json"));
    }
}
