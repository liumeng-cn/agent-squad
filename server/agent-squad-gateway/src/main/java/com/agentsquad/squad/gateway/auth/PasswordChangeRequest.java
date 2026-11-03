package com.agentsquad.squad.gateway.auth;

/** 已登录用户修改密码。 */
public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
