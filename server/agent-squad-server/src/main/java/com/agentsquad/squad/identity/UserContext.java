package com.agentsquad.squad.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 网关传入的用户上下文。
 * <p>
 * 业务模块从请求属性读取主体，不解析会话和 CSRF。
 */
public final class UserContext {
    public static final String ATTRIBUTE = UserContext.class.getName();

    private UserContext() {
    }

    public static AuthView current(HttpServletRequest request) {
        Object actor = request.getAttribute(ATTRIBUTE);
        if (actor instanceof AuthView view) {
            return view;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少网关用户上下文");
    }
}
