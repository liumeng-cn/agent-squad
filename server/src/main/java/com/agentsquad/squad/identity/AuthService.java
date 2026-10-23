package com.agentsquad.squad.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地开发身份服务。
 *
 * <p>当前仅提供明确标记的开发账号，令牌保存在进程内；正式实现需迁移到账号/认证会话表，
 * 保存令牌摘要、过期时间、authVersion，并在写请求增加 CSRF 校验。</p>
 */
@Service
public class AuthService {

    @Value("${agentsquad.auth.username:}")
    private String configuredUsername;

    @Value("${agentsquad.auth.password:}")
    private String configuredPassword;

    @Value("${agentsquad.auth.user-id}")
    private String configuredUserId;

    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

    /**
     * 登录成功后生成不可预测的临时会话令牌，不记录密码。
     */
    public String login(String username, String password) {
        if (configuredUsername.isBlank() || configuredPassword.isBlank()
                || !configuredUsername.equals(username) || !configuredPassword.equals(password)) {
            throw new AuthenticationRequiredException();
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, new AuthSession(configuredUserId, username, "USER",
                UUID.randomUUID().toString()));
        return token;
    }

    /**
     * 校验会话令牌并返回当前主体，失败时不泄露账号是否存在。
     */
    public AuthView authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationRequiredException();
        }
        AuthSession session = sessions.get(token);
        if (session == null) {
            throw new AuthenticationRequiredException();
        }
        return new AuthView(session.userId(), session.username(), List.of(session.role()), session.csrfToken());
    }

    /**
     * 写请求须同时持有 Cookie 和登录时签发的 CSRF 值；返回完整主体供服务层取归属与角色。
     */
    public AuthView authenticateWrite(String token, String csrfToken) {
        AuthView view = authenticate(token);
        if (csrfToken == null || !view.csrfToken().equals(csrfToken)) {
            throw new AuthenticationRequiredException();
        }
        return view;
    }

    /**
     * 注销当前会话，使后续请求立即失效。
     */
    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

}
