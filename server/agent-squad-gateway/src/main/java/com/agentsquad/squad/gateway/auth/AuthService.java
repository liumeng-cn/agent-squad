package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.gateway.common.BlockingCalls;
import com.agentsquad.squad.gateway.exception.AuthenticationRequiredException;
import com.agentsquad.squad.gateway.exception.PasswordChangeRequiredException;
import com.agentsquad.squad.gateway.exception.RateLimitedException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 网关认证与会话服务。
 *
 * <p>认证状态持久化在 MySQL；JDBC 与密码计算全部调度到 boundedElastic，避免阻塞 Netty event-loop。
 * 登录失败按规范化用户名与来源 IP 分别计数，账号停用、授权代际变化或会话过期均返回统一 401。</p>
 */
@Slf4j
@Service
public class AuthService {

    @Resource
    private AuthRepository authRepository;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthProperties properties;

    private String dummyPasswordHash;

    @PostConstruct
    void initialize() {
        dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 校验凭据并创建持久会话；登录失败不区分账号不存在、密码错误或已停用。
     */
    public Mono<LoginResult> login(String username, String password, String clientIp, String requestId) {
        return blocking(() -> doLogin(username, password, clientIp, requestId));
    }

    /**
     * 校验 Cookie 会话并返回当前主体；必要时刷新空闲过期时间且不超过绝对上限。
     */
    public Mono<AuthView> authenticate(String token, String requestId) {
        return blocking(() -> toView(requireSession(token, requestId), token));
    }

    /**
     * 写请求在会话校验之外再校验 CSRF 摘要。
     */
    public Mono<AuthView> authenticateWrite(String token, String csrfToken, String requestId) {
        return blocking(() -> {
            AuthSessionRow session = requireSession(token, requestId);
            if (!SessionSecrets.matches(csrfToken, session.csrfHash())) {
                throw new AuthenticationRequiredException();
            }
            return toView(session, token);
        });
    }

    /**
     * 注销当前会话；CSRF 不匹配时仍按未认证处理。
     */
    public Mono<Void> logout(String token, String csrfToken, String requestId) {
        return blocking(() -> {
            AuthSessionRow session = requireSession(token, requestId);
            if (!SessionSecrets.matches(csrfToken, session.csrfHash())) {
                throw new AuthenticationRequiredException();
            }
            authRepository.revokeSession(session.sessionId(), LocalDateTime.now());
            authRepository.insertAudit(session.userId(), session.userId(), "LOGOUT", "SUCCESS", requestId);
            return null;
        });
    }

    /**
     * 校验临时密码状态；未改密账号只允许受限入口。
     */
    public void requirePasswordChanged(AuthView view) {
        if (view.mustChangePassword()) {
            throw new PasswordChangeRequiredException();
        }
    }

    private LoginResult doLogin(String username, String password, String clientIp, String requestId) {
        String normalized = normalize(username);
        if (normalized.isEmpty() || password == null || password.isEmpty()) {
            throw new AuthenticationRequiredException();
        }
        LocalDateTime now = LocalDateTime.now();
        String userBucket = bucketKey("user", normalized, clientIp);
        String ipBucket = bucketKey("ip", "", clientIp);
        Optional<LocalDateTime> userLock = authRepository.lockedUntil(userBucket, now);
        Optional<LocalDateTime> ipLock = authRepository.lockedUntil(ipBucket, now);
        if (userLock.isPresent() || ipLock.isPresent()) {
            authRepository.insertAudit(null, null, "LOGIN", "DENIED", requestId);
            throw new RateLimitedException();
        }

        Optional<AuthAccount> account = authRepository.findAccountByUsername(normalized);
        String hash = account.map(AuthAccount::passwordHash).orElse(dummyPasswordHash);
        boolean matched = passwordEncoder.matches(password, hash);
        if (account.isEmpty() || !matched || !"ACTIVE".equals(account.get().status())) {
            authRepository.recordFailure(userBucket, now, properties);
            authRepository.recordFailure(ipBucket, now, properties);
            authRepository.insertAudit(account.map(AuthAccount::userId).orElse(null), null,
                    "LOGIN", "FAILURE", requestId);
            throw new AuthenticationRequiredException();
        }

        AuthAccount active = account.get();
        authRepository.clearFailures(userBucket);
        String token = SessionSecrets.randomToken();
        String csrfToken = SessionSecrets.csrfToken(token);
        LocalDateTime absoluteExpiresAt = now.plus(properties.getAbsoluteTimeout());
        LocalDateTime idleExpiresAt = earlier(now.plus(properties.getIdleTimeout()), absoluteExpiresAt);
        AuthSessionRow session = new AuthSessionRow(UUID.randomUUID().toString(), active.userId(),
                active.username(), active.role(), active.mustChangePassword(), SessionSecrets.sha256Hex(token),
                SessionSecrets.sha256Hex(csrfToken), active.authVersion(), active.authVersion(), active.status(),
                now, idleExpiresAt, absoluteExpiresAt, null);
        authRepository.insertSession(session, now);
        authRepository.insertAudit(active.userId(), active.userId(), "LOGIN", "SUCCESS", requestId);
        log.info("用户登录成功 userId={} requestId={}", active.userId(), requestId);
        return new LoginResult(token, new AuthView(active.userId(), active.username(), List.of(active.role()),
                csrfToken, active.mustChangePassword(), active.authVersion()));
    }

    private AuthSessionRow requireSession(String token, String requestId) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationRequiredException();
        }
        LocalDateTime now = LocalDateTime.now();
        AuthSessionRow session = authRepository.findSessionByTokenHash(SessionSecrets.sha256Hex(token))
                .orElseThrow(AuthenticationRequiredException::new);
        if (session.revokedAt() != null
                || !session.idleExpiresAt().isAfter(now)
                || !session.absoluteExpiresAt().isAfter(now)
                || !"ACTIVE".equals(session.accountStatus())
                || session.issuedAuthVersion() != session.accountAuthVersion()) {
            throw new AuthenticationRequiredException();
        }
        if (!session.lastSeenAt().plus(properties.getTouchInterval()).isAfter(now)) {
            LocalDateTime idleExpiresAt = earlier(now.plus(properties.getIdleTimeout()),
                    session.absoluteExpiresAt());
            authRepository.touchSession(session.sessionId(), now, idleExpiresAt);
        }
        return session;
    }

    private AuthView toView(AuthSessionRow session, String token) {
        return new AuthView(session.userId(), session.username(), List.of(session.role()),
                SessionSecrets.csrfToken(token), session.mustChangePassword(), session.issuedAuthVersion());
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String bucketKey(String scope, String value, String clientIp) {
        return SessionSecrets.sha256Hex(scope + "|" + value + "|" + (clientIp == null ? "" : clientIp));
    }

    private static LocalDateTime earlier(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private <T> Mono<T> blocking(Callable<T> callable) {
        return BlockingCalls.mono(callable);
    }
}
