package com.agentsquad.squad.gateway.filter;

import com.agentsquad.squad.gateway.auth.AuthService;
import com.agentsquad.squad.gateway.auth.AuthView;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 网关业务认证过滤器
 * <p>
 * 在内置路由转发前校验会话和写入防伪令牌，并以认证事实覆盖外部身份头
 */
@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    @Resource
    private AuthService authService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("SQUAD_SESSION");
            String token = cookie == null ? null : cookie.getValue();
            boolean read = Set.of("GET", "HEAD", "OPTIONS").contains(exchange.getRequest().getMethod().name());
            AuthView user = read ? authService.authenticate(token) : authService.authenticateWrite(token,
                    exchange.getRequest().getHeaders().getFirst("X-CSRF-Token"));
            var request = exchange.getRequest().mutate().headers(headers -> {
                headers.set("X-User-Id", user.userId());
                headers.set("X-User-Role", String.join(",", user.roles()));
                headers.set("X-User-Name", user.username());
                headers.remove("Cookie");
                headers.remove("X-CSRF-Token");
            }).build();
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
