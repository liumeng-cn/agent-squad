package com.agentsquad.squad.runtime.dto;

/**
 * 对话公开响应。
 */
public record ConversationResponse(String conversationId, String title, long generation,
                                   String status, long version, String activeTaskId) {
}
