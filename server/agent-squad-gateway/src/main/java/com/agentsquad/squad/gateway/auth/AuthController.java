package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.gateway.admin.UserAccountService;
import com.agentsquad.squad.gateway.common.BlockingCalls;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 网关登录协议入口。
 *
 * <p>负责 Cookie 写入、会话查询、改密与退出；凭据校验、限流和持久会话由认证服务负责，
 * 所有响应禁止缓存。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Resource
    private UserAccountService userAccountService;

    @Resource
    private AuthProperties authProperties;

    /**
     * 登录成功后写入 HttpOnly 会话 Cookie；临时密码状态由响应主体提示前端强制改密。
     */
    @PostMapping("/login")
    public Mono<AuthView> login(@RequestBody LoginRequest request, ServerHttpRequest httpRequest,
                                ServerHttpResponse response) {
        return authService.login(request.username(), request.password(), SessionRequestSupport.clientIp(httpRequest),
                        SessionRequestSupport.requestId(httpRequest))
                .doOnNext(result -> {
                    response.getHeaders().add(HttpHeaders.SET_COOKIE, sessionCookie(result.token(),
                            authProperties.isSecureCookie(), -1));
                    response.getHeaders().setCacheControl("no-store");
                })
                .map(LoginResult::view);
    }

    /**
     * 返回当前会话主体；`mustChangePassword` 为真时前端只能进入改密流程。
     */
    @GetMapping("/me")
    public Mono<AuthView> me(ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().setCacheControl("no-store");
        return authService.authenticate(SessionRequestSupport.readToken(request),
                SessionRequestSupport.requestId(request));
    }

    /**
     * 用户自行修改密码；成功后撤销全部会话，客户端必须重新登录。
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> changePassword(@RequestBody PasswordChangeRequest body, ServerHttpRequest request,
                                     ServerHttpResponse response) {
        response.getHeaders().setCacheControl("no-store");
        String requestId = SessionRequestSupport.requestId(request);
        return authService.authenticateWrite(SessionRequestSupport.readToken(request),
                        SessionRequestSupport.csrfToken(request), requestId)
                .flatMap(actor -> BlockingCalls.run(
                        () -> userAccountService.changePassword(actor, body, requestId)));
    }

    /**
     * 注销会话并清理客户端 Cookie。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(ServerHttpRequest request, ServerHttpResponse response) {
        return authService.logout(SessionRequestSupport.readToken(request),
                        SessionRequestSupport.csrfToken(request), SessionRequestSupport.requestId(request))
                .doOnSuccess(ignored -> {
                    response.getHeaders().setCacheControl("no-store");
                    response.getHeaders().add(HttpHeaders.SET_COOKIE,
                            sessionCookie("", authProperties.isSecureCookie(), 0));
                });
    }

    private static String sessionCookie(String token, boolean secure, long maxAgeSeconds) {
        return ResponseCookie.from(SessionRequestSupport.SESSION_COOKIE, token)
                .httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(maxAgeSeconds).build().toString();
    }
}
