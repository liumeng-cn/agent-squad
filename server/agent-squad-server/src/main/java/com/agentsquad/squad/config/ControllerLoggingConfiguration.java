package com.agentsquad.squad.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * MVC 控制器入口和出口日志配置
 *
 * <p>只记录匹配的路由模板和执行元数据，异步请求完成时统一记录出口。</p>
 */
@Slf4j
@Configuration
public class ControllerLoggingConfiguration implements WebMvcConfigurer, HandlerInterceptor {

    private static final String STARTED_AT = ControllerLoggingConfiguration.class.getName() + ".startedAt";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (log.isDebugEnabled() && handler instanceof HandlerMethod && request.getAttribute(STARTED_AT) == null) {
            request.setAttribute(STARTED_AT, System.nanoTime());
            log.debug("控制器请求进入 method={} route={}", request.getMethod(),
                    request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception failure) {
        if (request.getAttribute(STARTED_AT) instanceof Long started) {
            request.removeAttribute(STARTED_AT);
            log.debug("控制器请求结束 method={} route={} status={} elapsedMs={} errorType={}",
                    request.getMethod(), request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
                    response.getStatus(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    failure == null ? null : failure.getClass().getSimpleName());
        }
    }
}
