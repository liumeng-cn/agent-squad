package com.agentsquad.squad.service;

/**
 * 会话共享订阅的隔离键
 * <p>
 * 不同用户即使使用相同会话标识也不共享订阅或缓冲。
 */
public record ConversationStreamKey(String userId, String conversationId) {
}
