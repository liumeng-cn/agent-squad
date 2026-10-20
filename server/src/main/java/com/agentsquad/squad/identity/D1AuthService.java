package com.agentsquad.squad.identity;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D1 离线身份服务。
 *
 * <p>当前仅提供明确标记的开发账号，令牌保存在进程内；正式实现需迁移到账号/认证会话表，
 * 保存令牌摘要、过期时间、authVersion，并在写请求增加 CSRF 校验。</p>
 */
@Service
public class D1AuthService {

    private final Map<String, D1AuthSession> sessions = new ConcurrentHashMap<>();

    /** 登录成功后生成不可预测的临时会话令牌，不记录密码。 */
    public String login(String username, String password) {
        if (!"demo".equals(username) || !"demo".equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, new D1AuthSession("d1-demo-user", username, "USER"));
        return token;
    }

    /** 校验会话令牌并返回当前主体，失败时不泄露账号是否存在。 */
    public D1AuthView authenticate(String token) {
        D1AuthSession session = sessions.get(token);
        if (session == null) {
            throw new IllegalArgumentException("登录已失效");
        }
        return new D1AuthView(session.userId(), session.username(), session.role());
    }

    /** 注销当前会话，使后续请求立即失效。 */
    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

}
