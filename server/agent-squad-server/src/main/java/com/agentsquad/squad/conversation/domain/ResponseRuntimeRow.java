package com.agentsquad.squad.conversation.domain;

/**
 * 回复生命周期的持久裁决事实。
 * <p>
 * 与绑定、来源和完成候选同读，原生成功前不将候选正文公开。
 */
public record ResponseRuntimeRow(String executionId, String userId, String conversationId,
                                 String sourceMessageId, String scopeId, String bindingId, String status, long version,
                                 boolean cancellationRequested, String proposalJson, String proposalTurnId,
                                 String proposalHash,
                                 int correctionCount, int recoveryAttempts, String answerMessageId) {
}
