package com.agentsquad.squad.domain.entity;

/**
 * 会话公开事实的数据库查询结果
 * <p>
 * 字段只取同用户、同会话的已提交事实，不包含原生内部推理或运行候选。
 */
public record ConversationExecution(String executionId, String sourceMessageId, String status, long version,
                                    boolean cancellationRequested, String answerMessageId, String failureId) {
}
