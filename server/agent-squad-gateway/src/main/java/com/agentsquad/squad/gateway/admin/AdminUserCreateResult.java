package com.agentsquad.squad.gateway.admin;

/**
 * 创建账号结果；临时密码只在此响应出现一次，禁止日志记录。
 */
public record AdminUserCreateResult(String userId, String temporaryPassword) {
}
