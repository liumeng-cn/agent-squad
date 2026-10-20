package com.agentsquad.squad.runtime.domain;

/**
 * 对话持久层投影，只承载数据库事实。
 */
public record ConversationRow(String conversationId, String userId, String title, String status, String activeTaskId) {
}
