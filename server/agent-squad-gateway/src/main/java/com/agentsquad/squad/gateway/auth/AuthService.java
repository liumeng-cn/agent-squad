package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.common.identity.AccountRole;
import com.agentsquad.squad.common.identity.AccountStatus;
import com.agentsquad.squad.common.exception.AuthenticationRequiredException;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MySQL 持久身份权威：用户、密钥摘要、会话及授权撤销代际。
 * 不存储原始 Cookie/CSRF；网关实例共用数据库即可跨实例认证。
 */
@Service
public class AuthService {

    @Resource
    private AuthRepository repository;

    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    private String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private String randomToken(int length) {
        byte[] secret = new byte[length];
        random.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean same(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private String csrf(String token) {
        return digest("agent-squad:csrf:v1:" + token);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 只产生摘要入库；失败凭据和未知用户统一抛 401。
     * 限流记录以账号摘要为键并在同一 SQL 存储，避免多网关绕过。
     */
    @Transactional(noRollbackFor = AuthenticationRequiredException.class)
    public String login(String username, String password) {
        String name = normalize(username);
        String bucket = digest("login:" + name);
        LocalDateTime time = now();
        repository.ensureLoginBucket(bucket, time);
        RateBucket rate = repository.lockLoginBucket(bucket);
        if (rate != null && rate.lockedUntil() != null && rate.lockedUntil().isAfter(time)) {
            throw new AuthenticationRequiredException();
        }
        AuthAccount account = repository.findAccountByUsername(name).orElse(null);
        boolean ok = account != null && password != null && passwordEncoder.matches(password, account.passwordHash());
        if (!ok || account.status() != AccountStatus.ACTIVE) {
            repository.recordLoginFailure(bucket, time);
            throw new AuthenticationRequiredException();
        }
        repository.resetLoginFailures(bucket, time);
        String token = randomToken(32);
        String csrf = csrf(token);
        repository.insertSession(new AuthSessionRow(UUID.randomUUID().toString(), account.userId(),
                account.username(), account.role(), account.mustChangePassword(), digest(token), digest(csrf),
                account.authVersion(), account.authVersion(), account.status(), time,
                time.plusHours(24), time.plusDays(7), null), time);
        repository.insertAudit(account.userId(), account.userId(), "LOGIN", "SUCCESS", null);
        return token;
    }

    /**
     * 每次读账户当前状态/授权代际，禁用、改密和撤销即时失效。
     */
    public AuthView authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 256) {
            throw new AuthenticationRequiredException();
        }
        AuthSessionRow session = repository.findSessionByTokenHash(digest(token))
                .orElseThrow(AuthenticationRequiredException::new);
        LocalDateTime time = now();
        if (session.revokedAt() != null || session.accountStatus() != AccountStatus.ACTIVE
                || session.accountAuthVersion() != session.issuedAuthVersion()
                || !session.idleExpiresAt().isAfter(time) || !session.absoluteExpiresAt().isAfter(time)) {
            throw new AuthenticationRequiredException();
        }
        if (session.lastSeenAt().isBefore(time.minusMinutes(5))) {
            repository.touchSession(session.sessionId(), time, time.plusHours(24));
        }
        return new AuthView(session.userId(), session.username(), List.of(session.role()), csrf(token),
                session.mustChangePassword());
    }

    public AuthView authenticateWrite(String token, String csrfToken) {
        AuthView view = authenticate(token);
        if (csrfToken == null || !same(digest(view.csrfToken()), digest(csrfToken))) {
            throw new AuthenticationRequiredException();
        }
        return view;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            repository.findSessionByTokenHash(digest(token))
                    .ifPresent(session -> repository.revokeSession(session.sessionId(), now()));
        }
    }
}
