package com.agentsquad.squad.conversation.dto;

/**
 * 面向连续交流的会话视图。
 * <p>
 * 标题使用独立版本避免异步自动命名覆盖手动标题；最近活动时间由服务端提供。
 */
public record ConversationView(String conversationId, String title, String titleSource, long titleVersion,
                               long generation, String status, long version, String activeTaskId,
                               String lastActivityAt) {
}
