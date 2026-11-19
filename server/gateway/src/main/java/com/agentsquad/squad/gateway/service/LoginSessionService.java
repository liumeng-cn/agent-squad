package com.agentsquad.squad.gateway.service;

import com.agentsquad.squad.gateway.bo.LoginSessionBO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis登录会话生命周期
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Service
public class LoginSessionService {
    @Resource
    private ReactiveStringRedisTemplate redis;

    @Resource
    private ObjectMapper mapper;

    /**
     * 一次SET同时保存会话及TTL，Redis失败不返回登录成功。
     */
    public Mono<Void> create(LoginSessionBO session) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(session))
                .flatMap(value -> save(session, value))
                .flatMap(this::checkSaved);
    }

    public Mono<LoginSessionBO> read(String sessionId) {
        return redis.opsForValue().get(key(sessionId)).flatMap(this::deserialize);
    }

    private Mono<Boolean> save(LoginSessionBO session, String value) {
        return redis.opsForValue().set(key(session.getSessionId()), value,
                Duration.between(Instant.now(), session.getExpiresAt()));
    }

    private Mono<Void> checkSaved(Boolean saved) {
        if (Boolean.TRUE.equals(saved)) {
            return Mono.empty();
        }
        return Mono.error(new IllegalStateException("登录态保存失败"));
    }

    private Mono<LoginSessionBO> deserialize(String value) {
        return Mono.fromCallable(() -> mapper.readValue(value, LoginSessionBO.class));
    }

    public Mono<Void> revoke(String sessionId) {
        return redis.delete(key(sessionId)).then();
    }

    private String key(String sessionId) {
        return "squad:auth:session:" + sessionId;
    }
}
