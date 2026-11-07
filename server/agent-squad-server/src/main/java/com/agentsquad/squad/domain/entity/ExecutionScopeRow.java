package com.agentsquad.squad.domain.entity;

/**
 * 执行归属数据库记录
 * <p>
 * 固定用户、会话代际与预算账户；ownerId 按 RESPONSE/TASK 归属校验。
 */
public record ExecutionScopeRow(String scopeId, String userId, String conversationId,
                                long conversationGeneration, String kind, String ownerId, String accountId) {
}
