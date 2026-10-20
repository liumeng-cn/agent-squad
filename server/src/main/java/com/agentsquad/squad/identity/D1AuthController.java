package com.agentsquad.squad.identity;

import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * D1 登录协议入口。
 *
 * <p>Controller 只处理 Cookie 和 HTTP 状态，不把认证规则散落到任务接口；当前使用开发账号，
 * 生产版本需接入账号仓储和 CSRF 过滤器。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class D1AuthController {

    private static final String SESSION_COOKIE = "SQUAD_SESSION";

    @Resource
    private D1AuthService authService;

    /** 登录成功后写入 HttpOnly 会话 Cookie。 */
    @PostMapping("/login")
    public D1AuthView login(@RequestBody D1AuthRequest request, HttpServletResponse response) {
        String token = authService.login(request.username(), request.password());
        Cookie cookie = new Cookie(SESSION_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);
        return authService.authenticate(token);
    }

    /** 返回当前会话主体。 */
    @GetMapping("/me")
    public D1AuthView me(HttpServletRequest request) {
        return authService.authenticate(readToken(request));
    }

    /** 注销会话并清理客户端 Cookie。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(readToken(request));
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    private String readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new IllegalArgumentException("登录已失效");
        }
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        throw new IllegalArgumentException("登录已失效");
    }
}
