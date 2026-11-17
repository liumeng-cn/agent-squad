package com.agentsquad.squad.gateway.exceptions;

import com.agentsquad.squad.common.model.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关过滤链统一错误响应
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Component
@Order(-2)
public class GatewayErrorWebHandler implements ErrorWebExceptionHandler {
    @Resource
    private ObjectMapper mapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        var resolved = com.agentsquad.squad.common.exceptions.ExceptionResolver.resolve(error);
        exchange.getResponse().setRawStatusCode(resolved.getStatus());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        try {
            byte[] bytes = mapper.writeValueAsBytes(ApiResult.error(resolved.getCode(), resolved.getMessage()));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }
}
