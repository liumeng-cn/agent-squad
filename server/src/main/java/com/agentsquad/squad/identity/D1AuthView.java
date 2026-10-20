package com.agentsquad.squad.identity;

/** 当前认证主体的最小公开信息。 */
public record D1AuthView(String userId, String username, String role) {
}
