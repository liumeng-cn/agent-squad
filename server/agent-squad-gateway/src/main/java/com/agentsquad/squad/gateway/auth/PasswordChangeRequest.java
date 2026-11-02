package com.agentsquad.squad.gateway.auth;

/**
 * 用户自行修改密码请求；成功后所有会话失效并要求重新登录。
 */
public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
