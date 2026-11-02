package com.agentsquad.squad.gateway.filter;

import com.agentsquad.squad.gateway.assertion.AssertionSigner;
import com.agentsquad.squad.gateway.auth.AuthService;
import com.agentsquad.squad.gateway.auth.AuthView;
import com.agentsquad.squad.gateway.exception.PasswordChangeRequiredException;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 网关业务认证过滤器。
 *
 * <p>在路由转发前校验持久会话和写入防伪令牌，清除浏览器传入的内部身份头，
 * 并签发绑定本次方法/路径的短时 HMAC 断言；临时密码未修改的账号只能访问改密相关入口。</p>
 */
@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final List<String> INTERNAL_PREFIXES = List.of("x-user-", "x-squad-");

    @Resource
    private AuthService authService;

    @Resource
    private AssertionSigner assertionSigner;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("SQUAD_SESSION");
            String token = cookie == null ? null : cookie.getValue();
            String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
            boolean read = READ_METHODS.contains(exchange.getRequest().getMethod().name());
            Mono<AuthView> authentication = read
                    ? authService.authenticate(token, requestId)
                    : authService.authenticateWrite(token,
                            exchange.getRequest().getHeaders().getFirst("X-CSRF-Token"), requestId);
            return authentication
                    .map(this::requirePasswordChanged)
                    .flatMap(user -> chain.filter(exchange.mutate().request(forwardRequest(exchange, user)).build()));
        });
    }

    private AuthView requirePasswordChanged(AuthView user) {
        if (user.mustChangePassword()) {
            throw new PasswordChangeRequiredException();
        }
        return user;
    }

    private ServerHttpRequest forwardRequest(ServerWebExchange exchange, AuthView user) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String assertion = assertionSigner.sign(user.userId(), user.username(), user.roles(),
                user.authVersion(), exchange.getRequest().getMethod().name(),
                exchange.getRequest().getURI().getRawPath(), requestId, Instant.now());
        return exchange.getRequest().mutate().headers(headers -> {
            for (String name : new HashSet<>(headers.keySet())) {
                String lower = name.toLowerCase();
                if (lower.equals("cookie") || lower.equals("x-csrf-token")
                        || INTERNAL_PREFIXES.stream().anyMatch(lower::startsWith)) {
                    headers.remove(name);
                }
            }
            headers.set("X-Squad-Assertion", assertion);
        }).build();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
