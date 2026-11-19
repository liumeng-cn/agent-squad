package com.agentsquad.squad.gateway.filter;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.gateway.bo.AuthUserBO;
import org.springframework.http.HttpHeaders;
import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import com.agentsquad.squad.gateway.controller.AuthController;
import com.agentsquad.squad.gateway.service.AuthService;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 路由登录准入与可信身份转发
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {
    @Resource
    private AuthService authService;

    @Resource
    private AuthProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (Boolean.TRUE.equals(route.getMetadata().get("anonymous"))) {
            return chain.filter(exchange);
        }
        String audience = (String) route.getMetadata().get("audience");
        if (audience == null) {
            return Mono.error(new BusinessException(ApiCode.FORBIDDEN, "路由未配置访问权限"));
        }
        String method = exchange.getRequest().getMethod().name();
        boolean write = !Set.of("GET", "HEAD", "OPTIONS").contains(method);
        return authService.authenticate(AuthController.token(exchange), write,
                exchange.getRequest().getHeaders().getFirst("X-CSRF-Token")).flatMap(actor -> forwardAuthenticated(exchange, chain, audience, method, actor));
    }

    /** 签发内部身份并移除浏览器认证凭据 */
    private Mono<Void> forwardAuthenticated(ServerWebExchange exchange, GatewayFilterChain chain,
                                          String audience, String method, AuthUserBO actor) {
        CurrentUserBO user = new CurrentUserBO();
        user.setUserId(actor.getUser().getUserId());
        user.setSessionId(actor.getSession().getSessionId());
        String assertion = IdentityAssertion.sign(user, audience, method,
                exchange.getRequest().getURI().getRawPath(), properties.getIdentityKey());
        return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().headers(headers -> setIdentityHeaders(headers, assertion)).build()).build());
    }

    private void setIdentityHeaders(HttpHeaders headers, String assertion) {
        headers.set(IdentityAssertion.HEADER, assertion);
        headers.remove("Cookie");
        headers.remove("X-CSRF-Token");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
