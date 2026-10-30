package com.agentsquad.squad.presentation.domain;

/**
 * 历史消息关联的工作状态。
 * <p>
 * 取消等终态作为状态事实显示，不伪造一条模型生成的回答。
 */
public record ConversationTaskState(String taskId, String sourceMessageId, String status, long version) {
}
