package com.agentsquad.squad.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关业务路由配置。
 * <p>
 * 认证端点保留本地处理，业务请求由 Spring Cloud Gateway 内置过滤链转发。
 */
@Configuration
public class GatewayRoutingConfiguration {

    @Bean
    public RouteLocator businessRoutes(RouteLocatorBuilder builder,
                                       @Value("${gateway.business-service-url}") String businessUrl) {
        return builder.routes().route("business", route -> route.path("/api/**")
                .and().not(other -> other.path("/api/v1/auth/**"))
                .uri(businessUrl)).build();
    }
}
