package com.agentsquad.squad.gateway.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import com.agentsquad.squad.gateway.dto.LoginDTO;
import com.agentsquad.squad.gateway.service.AuthService;
import com.agentsquad.squad.gateway.vo.LoginVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关登录会话HTTP入口
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {
    public static final String COOKIE = "SQUAD_SESSION";
    @Resource
    private AuthService authService;
    @Resource
    private AuthProperties properties;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Mono<ApiResult<LoginVO>> login(@Valid @RequestBody LoginDTO request, ServerWebExchange exchange) {
        noStore(exchange);
        return authService.login(request).map(result -> {
            exchange.getResponse().addCookie(cookie(result.getToken(), properties.getLifetime().toSeconds()));
            return ApiResult.success(result.getView());
        });
    }

    /**
     * 获取登录信息
     */
    @GetMapping("/me")
    public Mono<ApiResult<LoginVO>> me(ServerWebExchange exchange) {
        noStore(exchange);
        return authService.authenticate(token(exchange), false, null).map(actor -> ApiResult.success(authService.view(actor)));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Mono<ApiResult<Void>> logout(ServerWebExchange exchange) {
        noStore(exchange);
        return authService.logout(token(exchange), exchange.getRequest().getHeaders().getFirst("X-CSRF-Token"))
                .then(Mono.fromSupplier(() -> {
                    exchange.getResponse().addCookie(cookie("", 0));
                    return ApiResult.<Void>success(null);
                }));
    }

    public static String token(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE);
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(properties.isSecureCookie())
                .sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    private void noStore(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().setCacheControl("no-store");
    }
}
