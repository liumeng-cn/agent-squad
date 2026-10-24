package com.agentsquad.squad.conversation.dto;

import java.util.List;

/**
 * 对话分页响应。
 */
public record ConversationPage(List<ConversationResponse> items, String nextCursor) {
}
