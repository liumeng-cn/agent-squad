package com.agentsquad.squad.gateway.auth;

import jakarta.annotation.Resource;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 邀请制用户管理入口；所有写操作必须持会话 Cookie 与 CSRF。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    @Resource private AuthService auth;
    @Resource private DatabaseAuthService users;

    private String token(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst("SQUAD_SESSION");
        return cookie == null ? null : cookie.getValue();
    }
    private AuthView actor(ServerHttpRequest request, String csrf) {
        return auth.authenticateWrite(token(request), csrf);
    }
    private void privateResponse(ServerHttpResponse response) {
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL,"no-store");
    }

    @GetMapping
    public Mono<UserPage> list(ServerHttpRequest request,ServerHttpResponse response,
                               @RequestParam(defaultValue="20") int limit,
                               @RequestParam(defaultValue="0") long after) {
        privateResponse(response);
        return Mono.fromCallable(() -> {
            AuthView view=auth.authenticate(token(request));
            return users.users(view,limit,after);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserCreateReceipt> create(ServerHttpRequest request,ServerHttpResponse response,
                           @RequestHeader("X-CSRF-Token") String csrf,
                           @RequestBody UserCreateRequest body) {
        privateResponse(response);
        return Mono.fromCallable(()->users.createUser(actor(request,csrf),body))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> update(ServerHttpRequest request,ServerHttpResponse response,
                       @RequestHeader("X-CSRF-Token") String csrf,@PathVariable String userId,
                       @RequestBody UserUpdateRequest body) {
        privateResponse(response);
        return Mono.fromRunnable(()->users.updateUser(actor(request,csrf),userId,body))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    @PostMapping("/{userId}/reset-password")
    public Mono<UserCreateReceipt> reset(ServerHttpRequest request,ServerHttpResponse response,
                           @RequestHeader("X-CSRF-Token") String csrf,@PathVariable String userId) {
        privateResponse(response);
        return Mono.fromCallable(()->users.resetPassword(actor(request,csrf),userId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
