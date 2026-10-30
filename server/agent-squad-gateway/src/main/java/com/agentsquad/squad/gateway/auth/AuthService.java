package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.gateway.exception.AuthenticationRequiredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地开发身份服务。
 *
 * <p>网关认证模块使用配置预置账号和进程内会话；负责凭据校验、主体解析与写请求 CSRF 校验，
 * 不访问业务数据库或转发业务请求。</p>
 */
@Service
public class AuthService {

    @Value("${agentsquad.auth.username:}")
    private String configuredUsername;

    @Value("${agentsquad.auth.password:}")
    private String configuredPassword;

    @Value("${agentsquad.auth.user-id}")
    private String configuredUserId;

    @Value("${agentsquad.auth.role:ADMIN}")
    private String configuredRole;

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
        sessions.put(token, new AuthSession(configuredUserId, username, configuredRole,
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
