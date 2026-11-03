package com.agentsquad.squad.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 统一请求关联 ID。日志不记录认证令牌或用户原始对话正文。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String incoming=request.getHeader("X-Request-Id");
        String id=incoming!=null && incoming.matches("[0-9a-fA-F-]{36}") ? incoming : UUID.randomUUID().toString();
        MDC.put("requestId",id);
        response.setHeader("X-Request-Id",id);
        try { chain.doFilter(request,response); }
        finally { MDC.remove("requestId"); }
    }
}
