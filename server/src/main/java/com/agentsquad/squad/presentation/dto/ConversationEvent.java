package com.agentsquad.squad.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 会话公开增量事件。
 * <p>
 * generation与sequence共同界定订阅水位，payload仅包含已提交的公开事实。
 */
public record ConversationEvent(String conversationId, long generation, long sequence, String type,
                                String occurredAt, JsonNode payload) {
}
