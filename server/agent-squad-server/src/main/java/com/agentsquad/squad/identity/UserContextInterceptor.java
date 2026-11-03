package com.agentsquad.squad.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Resource;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 业务服务的网关身份接入边界。
 * <p>
 * 仅接收部署可信网关写入的身份头，业务端口应限制为内部访问。
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {
    @Resource
    private SignedIdentityVerifier verifier;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthView assertion = verifier.verify(request);
        if (assertion != null) {
            request.setAttribute(UserContext.ATTRIBUTE, assertion);
            return true;
        }
        // 无断言仅允许显式的本地非生产兼容模式，prod 配置严格拒绝。
        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少网关身份");
        }
        String username = request.getHeader("X-User-Name");
        request.setAttribute(UserContext.ATTRIBUTE,
                new AuthView(userId, username == null ? userId : username, List.of(role), null));
        return true;
    }
}
