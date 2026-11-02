package com.agentsquad.squad.gateway.admin;

/**
 * 重置密码结果；临时密码只在此响应出现一次，禁止日志记录。
 */
public record ResetPasswordResult(String temporaryPassword) {
}
