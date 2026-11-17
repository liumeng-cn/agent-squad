package com.agentsquad.squad.gateway.exceptions;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.model.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
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
        ApiCode code = ApiCode.SERVICE_UNAVAILABLE;
        int status = 503;
        String message = code.getMsg();
        if (error instanceof BusinessException business) {
            code = business.getCode();
            status = code == ApiCode.UNAUTHORIZED ? 401 : code == ApiCode.FORBIDDEN ? 403 : 200;
            message = business.getMessage();
        } else if (error instanceof WebExchangeBindException) {
            code = ApiCode.BAD_REQUEST;
            status = 200;
            message = "手机号或验证码格式无效";
        } else if (error instanceof ResponseStatusException response) {
            status = response.getStatusCode().value();
            code = ApiCode.fromCode(status);
            message = code.getMsg();
        }
        exchange.getResponse().setRawStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        try {
            byte[] bytes = mapper.writeValueAsBytes(ApiResult.error(code, message));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }
}
