package com.agentsquad.squad.gateway.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.UserStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.gateway.bo.AuthUserBO;
import com.agentsquad.squad.gateway.bo.LoginResultBO;
import com.agentsquad.squad.gateway.bo.LoginSessionBO;
import com.agentsquad.squad.gateway.client.UserAccountClient;
import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import com.agentsquad.squad.gateway.dto.LoginDTO;
import com.agentsquad.squad.gateway.vo.LoginVO;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import jakarta.annotation.Resource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JWT登录与请求认证
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Service
public class AuthService {
    @Resource
    private AuthProperties properties;
    @Resource
    private UserAccountClient users;
    @Resource
    private LoginSessionService sessions;
    @Resource
    private JwtEncoder encoder;
    @Resource
    private JwtDecoder decoder;

    /**
     * 验证登录凭据后创建Redis会话，只有保存成功才交付JWT。
     */
    public Mono<LoginResultBO> login(LoginDTO request) {
        if (!properties.getVerificationCode().equals(request.getVerificationCode())) {
            return Mono.error(new BusinessException(ApiCode.BAD_REQUEST, "验证码错误"));
        }
        return users.byPhone(request.getPhone()).flatMap(this::createLoginSession);
    }

    /** 创建会话并在持久化成功后交付登录结果 */
    private Mono<LoginResultBO> createLoginSession(UserAccountVO user) {
        requireActive(user);
        LoginSessionBO session = new LoginSessionBO();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getUserId());
        session.setAuthVersion(user.getAuthVersion());
        session.setCsrfToken(UUID.randomUUID().toString() + UUID.randomUUID());
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(session.getCreatedAt().plus(properties.getLifetime()));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience())).subject(user.getUserId()).id(session.getSessionId())
                .issuedAt(session.getCreatedAt()).expiresAt(session.getExpiresAt())
                .claim("authVersion", user.getAuthVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        LoginResultBO result = new LoginResultBO();
        result.setToken(token);
        result.setView(view(user, session));
        return sessions.create(session).thenReturn(result);
    }

    /**
     * JWT、Redis与当前账号事实三者同时有效才建立可信身份。
     */
    public Mono<AuthUserBO> authenticate(String token, boolean write, String csrf) {
        return Mono.fromCallable(() -> decode(token))
                .flatMap(jwt -> authenticateSession(jwt, write, csrf));
    }

    /** 校验令牌关联的会话及写请求凭据 */
    private Mono<AuthUserBO> authenticateSession(Jwt jwt, boolean write, String csrf) {
        return sessions.read(jwt.getId())
                .switchIfEmpty(Mono.error(unauthorized()))
                .flatMap(session -> authenticateUser(jwt, session, write, csrf));
    }

    private Mono<AuthUserBO> authenticateUser(Jwt jwt, LoginSessionBO session, boolean write, String csrf) {
        Number version = jwt.getClaim("authVersion");
        if (!session.getUserId().equals(jwt.getSubject()) || !session.getExpiresAt().isAfter(Instant.now())
                || version == null || session.getAuthVersion() != version.longValue()) {
            return Mono.error(unauthorized());
        }
        if (write && (csrf == null || !MessageDigest.isEqual(csrf.getBytes(StandardCharsets.UTF_8),
                session.getCsrfToken().getBytes(StandardCharsets.UTF_8)))) {
            return Mono.error(new BusinessException(ApiCode.FORBIDDEN, "请求校验失败，请刷新后重试"));
        }
        return users.byId(session.getUserId())
                .onErrorMap(BusinessException.class, this::mapAccountError)
                .map(user -> createActor(user, session));
    }

    private BusinessException mapAccountError(BusinessException exception) {
        if (exception.getCode() == ApiCode.NOT_FOUND) {
            return unauthorized();
        }
        return exception;
    }

    private AuthUserBO createActor(UserAccountVO user, LoginSessionBO session) {
        requireActive(user);
        if (user.getAuthVersion() != session.getAuthVersion()) {
            throw unauthorized();
        }
        AuthUserBO actor = new AuthUserBO();
        actor.setUser(user);
        actor.setSession(session);
        return actor;
    }

    public LoginVO view(AuthUserBO actor) {
        return view(actor.getUser(), actor.getSession());
    }

    public Mono<Void> logout(String token, String csrf) {
        return authenticate(token, true, csrf).flatMap(this::revokeSession)
                .onErrorResume(BusinessException.class, this::handleLogoutError);
    }

    private Mono<Void> revokeSession(AuthUserBO actor) {
        return sessions.revoke(actor.getSession().getSessionId());
    }

    private Mono<Void> handleLogoutError(BusinessException exception) {
        if (exception.getCode() == ApiCode.UNAUTHORIZED) {
            return Mono.empty();
        }
        return Mono.error(exception);
    }

    private Jwt decode(String token) {
        try {
            if (token == null || token.length() > 8192) {
                throw unauthorized();
            }
            Jwt jwt = decoder.decode(token);
            if (!jwt.getAudience().contains(properties.getAudience()) || jwt.getId() == null
                    || jwt.getSubject() == null || !jwt.getSubject().matches("[1-9][0-9]{0,18}")
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())) {
                throw unauthorized();
            }
            return jwt;
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private LoginVO view(UserAccountVO user, LoginSessionBO session) {
        LoginVO result = new LoginVO();
        result.setUser(user);
        result.setCsrfToken(session.getCsrfToken());
        result.setExpiresAt(session.getExpiresAt());
        return result;
    }

    private void requireActive(UserAccountVO user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ApiCode.FORBIDDEN, "账号已禁用");
        }
    }

    private BusinessException unauthorized() {
        return new BusinessException(ApiCode.UNAUTHORIZED, "登录已失效，请重新登录");
    }
}
