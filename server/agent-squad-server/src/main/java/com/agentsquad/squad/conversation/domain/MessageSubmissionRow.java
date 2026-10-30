package com.agentsquad.squad.conversation.domain;

/**
 * 消息受理的持久幂等投影。
 * <p>
 * 收据身份与原始排队水位取自同一Command、回复执行和会话事件，不从客户端回填。
 */
public record MessageSubmissionRow(String conversationId, String messageId, String executionId,
                                   String commandId, String scopeId, String payloadHash, String status,
                                   long generation, long receiptSequence) {
}
