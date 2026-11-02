package com.agentsquad.squad.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.util.UUID;

/** 统一替换来自浏览器的请求标识，跨网关业务调用保持同一个可审计关联 ID。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRequestIdFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId=UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Request-Id",requestId);
        var req=exchange.getRequest().mutate().headers(h->h.set("X-Request-Id",requestId)).build();
        return chain.filter(exchange.mutate().request(req).build());
    }
}
