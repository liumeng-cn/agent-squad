package com.agentsquad.squad.conversation.domain;

/**
 * 对话数据库查询结果，只承载数据库事实
 */
public record ConversationRow(long id, String conversationId, String userId, String title, String status,
                              String activeTaskId, long generation, long version) {
}
