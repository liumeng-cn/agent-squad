package com.agentsquad.squad.gateway.auth;

/** 公开用户摘要，不含密码和会话。 */
public record UserSummary(String userId, String username, String displayName, String role,
                          String status, boolean mustChangePassword, long version) {
}
