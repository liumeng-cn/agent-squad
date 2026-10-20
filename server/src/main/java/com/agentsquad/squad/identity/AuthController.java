package com.agentsquad.squad.identity;

import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

/**
 * 本地开发登录协议入口。
 *
 * <p>Controller 只处理 Cookie 和 HTTP 状态，不把认证规则散落到任务接口；当前使用开发账号，
 * 生产版本需接入账号仓储和 CSRF 过滤器。</p>
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
    public AuthView login(@RequestBody LoginRequest request, HttpServletResponse response) {
        String token = authService.login(request.username(), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true).sameSite("Lax").path("/").build().toString());
        return authService.authenticate(token);
    }

    /**
     * 返回当前会话主体。
     */
    @GetMapping("/me")
    public AuthView me(HttpServletRequest request) {
        return authService.authenticate(readToken(request));
    }

    /**
     * 注销会话并清理客户端 Cookie。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       @RequestHeader("X-CSRF-Token") String csrfToken) {
        authService.authenticateWrite(readToken(request), csrfToken);
        authService.logout(readToken(request));
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true).sameSite("Lax").path("/").maxAge(0).build().toString());
    }

    private String readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new AuthenticationRequiredException();
        }
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        throw new AuthenticationRequiredException();
    }
}
