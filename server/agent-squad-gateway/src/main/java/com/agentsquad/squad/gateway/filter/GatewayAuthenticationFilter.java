package com.agentsquad.squad.gateway.filter;

import com.agentsquad.squad.gateway.auth.AuthService;
import com.agentsquad.squad.gateway.auth.AuthView;
import com.agentsquad.squad.gateway.auth.InternalIdentitySigner;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    @Resource
    private InternalIdentitySigner signer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.fromCallable(() -> {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("SQUAD_SESSION");
            String token = cookie == null ? null : cookie.getValue();
            boolean read = Set.of("GET", "HEAD", "OPTIONS").contains(exchange.getRequest().getMethod().name());
            return read ? authService.authenticate(token)
                    : authService.authenticateWrite(token,exchange.getRequest().getHeaders().getFirst("X-CSRF-Token"));
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(user -> {
            String assertion=signer.sign(user,exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getURI().getRawPath());
            var request=exchange.getRequest().mutate().headers(headers -> {
                // 防止来自浏览器的内部身份头透传。
                headers.remove("X-User-Id");
                headers.remove("X-User-Role");
                headers.remove("X-User-Name");
                headers.remove("X-Squad-Assertion");
                if(assertion != null) headers.set("X-Squad-Assertion",assertion);
                headers.set("X-User-Id",user.userId());
                headers.set("X-User-Role",String.join(",",user.roles()));
                headers.set("X-User-Name",user.username());
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
