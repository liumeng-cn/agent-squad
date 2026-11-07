package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.gateway.service.AuthService;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.gateway.service.UserAccountService;
import com.agentsquad.squad.gateway.common.BlockingCalls;
import com.agentsquad.squad.common.exception.AuthenticationRequiredException;
import jakarta.annotation.Resource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

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

    @Resource
    private UserAccountService userAccountService;

    @Resource
    private AuthProperties authProperties;

    /**
     * 登录成功后写入 HttpOnly 会话 Cookie。
     */
    @PostMapping("/login")
    public Mono<ApiResult<AuthView>> login(@RequestBody LoginRequest request, ServerHttpResponse response) {
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        return Mono.fromCallable(() -> {
            String token = authService.login(request.username(), request.password());
            response.getHeaders().add(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, token)
                    .httpOnly(true).secure(authProperties.isSecureCookie()).sameSite("Lax").path("/").build()
                    .toString());
            return authService.authenticate(token);
        }).map(ApiResult::success).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 返回当前会话主体。
     */
    @GetMapping("/me")
    public Mono<ApiResult<AuthView>> me(ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        return Mono.fromCallable(() -> authService.authenticate(readToken(request)))
                .map(ApiResult::success).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 注销会话并清理客户端 Cookie。
     */
    @PostMapping("/logout")
    public Mono<ApiResult<Void>> logout(ServerHttpRequest request, ServerHttpResponse response,
                       @RequestHeader("X-CSRF-Token") String csrfToken) {
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        return Mono.fromRunnable(() -> {
            authService.authenticateWrite(readToken(request), csrfToken);
            authService.logout(readToken(request));
            response.getHeaders().add(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, "")
                    .httpOnly(true).secure(authProperties.isSecureCookie()).sameSite("Lax").path("/").maxAge(0)
                    .build().toString());
        }).subscribeOn(Schedulers.boundedElastic()).thenReturn(ApiResult.success(null));
    }

    @PostMapping("/password")
    public Mono<ApiResult<Void>> changePassword(ServerHttpRequest request, ServerHttpResponse response,
            @RequestHeader("X-CSRF-Token") String csrf, @RequestBody PasswordChangeRequest change) {
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        String requestId = SessionRequestSupport.requestId(request);
        return BlockingCalls.run(() -> {
            AuthView actor = authService.authenticateWrite(readToken(request), csrf);
            userAccountService.changePassword(actor, change, requestId);
        }).thenReturn(ApiResult.success(null));
    }

    private String readToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(SESSION_COOKIE);
        if (cookie == null) {
            throw new AuthenticationRequiredException();
        }
        return cookie.getValue();
    }
}
