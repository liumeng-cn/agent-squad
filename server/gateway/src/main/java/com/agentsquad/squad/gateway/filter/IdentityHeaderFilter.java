package com.agentsquad.squad.gateway.filter;

import com.agentsquad.squad.common.utils.IdentityAssertion;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 清理所有外部内部身份头
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Component
public class IdentityHeaderFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().headers(this::removeIdentityHeaders).build()).build());
    }

    private void removeIdentityHeaders(HttpHeaders headers) {
        headers.remove("X-User-Id");
        headers.remove("X-User-Name");
        headers.remove("X-User-Role");
        headers.remove("X-Service-Key");
        headers.remove("X-Squad-Assertion");
        headers.remove(IdentityAssertion.HEADER);
    }
}
