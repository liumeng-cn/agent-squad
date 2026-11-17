package com.agentsquad.squad.v2.filter;

import com.agentsquad.squad.common.utils.IdentityAssertion;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部服务身份边界校验
 * <p>
 * 所属v2-server模块，按明确职责维护用户身份与数据边界。
 */
@Component
public class TrustedIdentityFilter extends OncePerRequestFilter {
    @Resource
    private Environment environment;

    @Resource
    private com.agentsquad.squad.common.web.GlobalExceptionHandler exceptions;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            if (path.startsWith("/api/")) {
                request.setAttribute(IdentityAssertion.ATTRIBUTE, IdentityAssertion.verify(
                        request.getHeader(IdentityAssertion.HEADER), "v2-server", request.getMethod(), path,
                        environment.getRequiredProperty("agentsquad.identity.identity-key")));
            }
        } catch (IllegalArgumentException exception) {
            exceptions.write(response, new com.agentsquad.squad.common.exceptions.BusinessException(
                    com.agentsquad.squad.common.enums.ApiCode.UNAUTHORIZED, "缺少有效身份"));
            return;
        }
        chain.doFilter(request, response);
    }
}
