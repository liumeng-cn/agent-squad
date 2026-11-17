package com.agentsquad.squad.user.server.filter;

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
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Component
public class TrustedIdentityFilter extends OncePerRequestFilter {
    @Resource
    private Environment environment;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            if (path.startsWith("/api/") && !(path.equals("/api/v2/users/register") && request.getMethod().equals("POST"))) {
                request.setAttribute(IdentityAssertion.ATTRIBUTE, IdentityAssertion.verify(
                        request.getHeader(IdentityAssertion.HEADER), "user-server", request.getMethod(), path,
                        environment.getRequiredProperty("agentsquad.user.identity-key")));
            }
        } catch (IllegalArgumentException exception) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"data\":null,\"msg\":\"缺少有效身份\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
