package com.agentsquad.squad.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 统一处理跨网关请求标识。
 * <p>
 * 合法入站 requestId 直接复用，缺失或格式非法时重新生成，保证外部伪造值不会进入日志和下游；
 * W3C 追踪头由追踪组件从入站请求解析并写入下游请求，这里只负责 requestId 与 Reactor 上下文。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRequestIdFilter implements WebFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_CONTEXT_KEY = "requestId";
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = incoming != null && REQUEST_ID_PATTERN.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        var request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
                .build();
        // 只写入 Reactor 上下文，由上下文传播机制在异步切换时恢复 MDC，避免 ThreadLocal 跨请求串号。
        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(context -> context.put(REQUEST_ID_CONTEXT_KEY, requestId));
    }
}
