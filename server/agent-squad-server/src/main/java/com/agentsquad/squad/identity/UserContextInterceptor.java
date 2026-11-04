package com.agentsquad.squad.identity;

import com.agentsquad.squad.common.enums.identity.AccountRole;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;

/**
 * 业务服务的网关身份接入边界。
 *
 * <p>优先验证网关签名断言并复核账号状态；未签名头只允许显式开启的本地非生产兼容模式，
 * 生产严格模式下即使误开本地兼容也不接受浏览器伪造的身份头。</p>
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Resource
    private SignedIdentityVerifier verifier;

    @Resource
    private UserStatusGuard userStatus;

    @Value("${agentsquad.identity.strict:false}")
    private boolean strict;

    @Value("${agentsquad.identity.allow-unsigned-local:false}")
    private boolean allowUnsignedLocal;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthView assertion = verifier.verify(request);
        if (assertion != null) {
            userStatus.requireActive(assertion.userId());
            request.setAttribute(UserContext.ATTRIBUTE, assertion);
            return true;
        }
        if (strict || !allowUnsignedLocal) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "业务服务拒绝未签名身份");
        }

        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        if (userId == null || role == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少有效的本地开发身份");
        }
        final AccountRole accountRole;
        try {
            accountRole = AccountRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "本地开发角色无效");
        }
        try {
            userId = UUID.fromString(userId).toString();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "本地开发用户标识格式无效");
        }

        userStatus.requireActive(userId);
        String username = request.getHeader("X-User-Name");
        request.setAttribute(UserContext.ATTRIBUTE,
                new AuthView(userId, username == null ? userId : username, List.of(accountRole), null));
        return true;
    }
}
