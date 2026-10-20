package com.agentsquad.squad.identity;

/** D1 离线会话内部投影，正式版由认证会话持久层替代。 */
public record D1AuthSession(String userId, String username, String role) {
}
