package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.gateway.exception.AuthenticationRequiredException;
import jakarta.annotation.Resource;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 本地开发登录协议入口。
 *
 * <p>网关认证模块的 Reactive HTTP 入口，只处理登录、会话查询、退出及 Cookie，
 * 凭据与 CSRF 规则由认证服务负责。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String SESSION_COOKIE = "SQUAD_SESSION";

    @Resource
    private AuthService authService;

    /**
     * 登录成功后写入 HttpOnly 会话 Cookie。
     */
    @PostMapping("/login")
    public AuthView login(@RequestBody LoginRequest request, ServerHttpResponse response) {
        String token = authService.login(request.username(), request.password());
        response.getHeaders().add(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true).sameSite("Lax").path("/").build().toString());
        return authService.authenticate(token);
    }

    /**
     * 返回当前会话主体。
     */
    @GetMapping("/me")
    public AuthView me(ServerHttpRequest request) {
        return authService.authenticate(readToken(request));
    }

    /**
     * 注销会话并清理客户端 Cookie。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(ServerHttpRequest request, ServerHttpResponse response,
                       @RequestHeader("X-CSRF-Token") String csrfToken) {
        authService.authenticateWrite(readToken(request), csrfToken);
        authService.logout(readToken(request));
        response.getHeaders().add(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true).sameSite("Lax").path("/").maxAge(0).build().toString());
    }

    private String readToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(SESSION_COOKIE);
        if (cookie == null) {
            throw new AuthenticationRequiredException();
        }
        return cookie.getValue();
    }
}
