package com.agentsquad.squad.conversation.dto;

/**
 * 对话公开响应。
 */
public record ConversationResponse(String conversationId, String title, long generation,
                                   String status, long version, String activeTaskId) {
}
