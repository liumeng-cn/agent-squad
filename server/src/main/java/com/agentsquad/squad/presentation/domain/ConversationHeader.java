package com.agentsquad.squad.presentation.domain;

/**
 * 会话头与一致水位的持久读取投影。
 * <p>
 * 字段只取同用户、同会话的已提交事实，不包含原生内部推理或运行候选。
 */
public record ConversationHeader(String conversationId, String title, String titleSource, long titleVersion,
                                 String status, long generation, long version, String activeTaskId, long eventSequence,
                                 java.time.LocalDateTime lastActivityAt) {
}
