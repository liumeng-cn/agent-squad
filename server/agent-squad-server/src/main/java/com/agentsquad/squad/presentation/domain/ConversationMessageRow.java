package com.agentsquad.squad.presentation.domain;

/**
 * 会话公开事实的持久读取投影。
 * <p>
 * 字段只取同用户、同会话的已提交事实，不包含原生内部推理或运行候选。
 */
public record ConversationMessageRow(String messageId, long sequence, String role, String text, long revision,
                                     boolean completed, String sourceMessageId, String executionId, String taskId,
                                     java.time.LocalDateTime occurredAt) {
}
