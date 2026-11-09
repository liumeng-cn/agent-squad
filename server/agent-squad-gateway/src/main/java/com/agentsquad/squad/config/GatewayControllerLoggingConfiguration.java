package com.agentsquad.squad.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

import java.util.concurrent.TimeUnit;

/**
 * 网关控制器入口和出口日志配置
 *
 * <p>记录网关本地认证与账号管理请求的执行状态和耗时，日志使用固定路由分组。</p>
 */
@Slf4j
@Configuration
public class GatewayControllerLoggingConfiguration {

    @Bean
    public WebFilter gatewayControllerLoggingFilter() {
        return (exchange, chain) -> {
            var path = exchange.getRequest().getPath().pathWithinApplication().value();
            String route = controllerRoute(path);
            if (route == null || !log.isDebugEnabled()) {
                return chain.filter(exchange);
            }

            long started = System.nanoTime();
            var method = exchange.getRequest().getMethod();
            log.debug("网关控制器请求进入 method={} route={}", method, route);
            return chain.filter(exchange).doFinally(signal ->
                    log.debug("网关控制器请求结束 method={} route={} status={} signal={} elapsedMs={}",
                            method, route, exchange.getResponse().getStatusCode(), signal,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        };
    }

    private String controllerRoute(String path) {
        if (path.equals("/api/v1/auth") || path.startsWith("/api/v1/auth/")) {
            return "/api/v1/auth";
        }
        if (path.equals("/api/v1/admin/users") || path.startsWith("/api/v1/admin/users/")) {
            return "/api/v1/admin/users";
        }
        return null;
    }
}
