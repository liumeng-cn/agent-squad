package com.agentsquad.squad.gateway.auth;

/** 管理员创建邀请制账号。 */
public record UserCreateRequest(String username, String displayName, String role) {
}
