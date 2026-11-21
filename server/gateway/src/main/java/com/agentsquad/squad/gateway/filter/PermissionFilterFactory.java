package com.agentsquad.squad.gateway.filter;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.user.api.client.UserClient;
import com.agentsquad.squad.user.api.dto.PermissionCheckDTO;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 管理路由鉴权过滤器
 * <p>
 * 路由谓词决定应用范围，用户服务决定是否授权；远程拒绝或失败均不继续转发。
 */
@Component
public class PermissionFilterFactory extends AbstractGatewayFilterFactory<Object> {
    @Resource
    private UserClient users;

    /**
     * 声明 Gateway 无参数过滤器的框架配置类型
     */
    public PermissionFilterFactory() {
        super(Object.class);
    }

    @Override
    public String name() {
        return "Permission";
    }

    @Override
    public GatewayFilter apply(Object config) {
        return this::filter;
    }

    private Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 用户编号来自登录会话，不采信浏览器提交的目标用户。
        CurrentUserBO actor = CurrentUserContext.getUser(exchange.getAttributes());
        PermissionCheckDTO request = new PermissionCheckDTO();
        request.setUserId(actor.getUserId());
        request.setMethod(exchange.getRequest().getMethod().name());
        request.setPath(exchange.getRequest().getURI().getRawPath());

        return Mono.fromCallable(() -> users.checkPermission(request))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(3))
                .switchIfEmpty(Mono.error(new BusinessException(ApiCode.FORBIDDEN, "权限查询响应为空")))
                .flatMap(this::requireAllowed)
                .then(Mono.defer(() -> chain.filter(exchange)));
    }

    private Mono<Void> requireAllowed(ApiResult<Boolean> response) {
        if (response.getCode() != 0 || !Boolean.TRUE.equals(response.getData())) {
            return Mono.error(new BusinessException(ApiCode.FORBIDDEN, "没有该管理操作权限"));
        }
        return Mono.empty();
    }
}
