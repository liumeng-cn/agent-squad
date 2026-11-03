package com.agentsquad.squad.gateway.auth;

/**
 * 本地开发模式的内存会话；只保存主体归属与该会话的 CSRF 值。
 */
public record AuthSession(String userId, String username, String role, String csrfToken) {
}
