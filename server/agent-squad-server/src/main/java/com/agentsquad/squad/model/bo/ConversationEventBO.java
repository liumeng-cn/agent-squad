package com.agentsquad.squad.model.bo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 会话公开增量事件。
 * <p>
 * generation与sequence共同界定订阅水位，payload仅包含已提交的公开事实。
 */
public record ConversationEventBO(String conversationId, long generation, long sequence, String type,
                                String occurredAt, JsonNode payload) {
}
