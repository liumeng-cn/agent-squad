package com.agentsquad.squad.gateway.auth;

/**
 * 认证所需的账号投影；密码摘要只用于服务端校验，不进入响应。
 */
public record AuthAccount(String userId, String username, String displayName, String passwordHash,
                          String role, String status, long authVersion, boolean mustChangePassword,
                          long version) {
}
