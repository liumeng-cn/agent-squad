package com.agentsquad.squad.utils;

import com.agentsquad.squad.exceptions.AuthenticationRequiredException;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 会话 Cookie 与请求元数据读取。
 */
public final class SessionRequestSupport {

    public static final String SESSION_COOKIE = "SQUAD_SESSION";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private SessionRequestSupport() {
    }

    public static String readToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(SESSION_COOKIE);
        if (cookie == null) {
            throw new AuthenticationRequiredException();
        }
        return cookie.getValue();
    }

    public static String csrfToken(ServerHttpRequest request) {
        return request.getHeaders().getFirst(CSRF_HEADER);
    }

    public static String requestId(ServerHttpRequest request) {
        String value = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return value == null ? "" : value;
    }

    public static String clientIp(ServerHttpRequest request) {
        return request.getRemoteAddress() == null ? ""
                : request.getRemoteAddress().getAddress().getHostAddress();
    }
}
