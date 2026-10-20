package com.agentsquad.squad.identity;

/** 本地开发会话内部投影，后续由认证会话持久层替代。 */
public record AuthSession(String userId, String username, String role, String csrfToken) {
}
