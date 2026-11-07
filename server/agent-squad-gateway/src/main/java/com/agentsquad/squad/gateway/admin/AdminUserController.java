package com.agentsquad.squad.gateway.admin;

import com.agentsquad.squad.gateway.service.UserAccountService;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.gateway.service.AuthService;
import com.agentsquad.squad.gateway.auth.SessionRequestSupport;
import com.agentsquad.squad.gateway.common.BlockingCalls;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 管理员账号管理接口。
 *
 * <p>路由不转发到业务服务；每次操作先校验持久会话与 CSRF，再在事务服务中校验管理员角色。
 * 所有响应禁止缓存，接口不返回密码摘要、会话令牌或完整 IP。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    @Resource
    private AuthService authService;

    @Resource
    private UserAccountService userAccountService;

    @GetMapping
    public Mono<ApiResult<AdminUserPage>> list(@RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit,
                                               ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().setCacheControl("no-store");
        String token = SessionRequestSupport.readToken(request);
        return BlockingCalls.mono(() -> userAccountService.list(authService.authenticate(token), cursor, limit))
                .map(ApiResult::success);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResult<AdminUserCreateResult>> create(@RequestBody AdminUserCreateRequest body,
                                                         ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().setCacheControl("no-store");
        String requestId = SessionRequestSupport.requestId(request);
        return BlockingCalls.mono(() -> userAccountService.create(
                authService.authenticateWrite(SessionRequestSupport.readToken(request),
                        SessionRequestSupport.csrfToken(request)),
                body, requestId)).map(ApiResult::success);
    }

    @PatchMapping("/{userId}")
    public Mono<ApiResult<AdminUserView>> update(@PathVariable String userId, @RequestBody AdminUserUpdateRequest body,
                                                 ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().setCacheControl("no-store");
        String requestId = SessionRequestSupport.requestId(request);
        return BlockingCalls.mono(() -> userAccountService.update(
                authService.authenticateWrite(SessionRequestSupport.readToken(request),
                        SessionRequestSupport.csrfToken(request)),
                userId, body, requestId)).map(ApiResult::success);
    }

    @PostMapping("/{userId}/reset-password")
    public Mono<ApiResult<ResetPasswordResult>> resetPassword(@PathVariable String userId,
                                                              ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().setCacheControl("no-store");
        String requestId = SessionRequestSupport.requestId(request);
        return BlockingCalls.mono(() -> userAccountService.resetPassword(
                authService.authenticateWrite(SessionRequestSupport.readToken(request),
                        SessionRequestSupport.csrfToken(request)),
                userId, requestId)).map(ApiResult::success);
    }
}
