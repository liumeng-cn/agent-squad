package com.agentsquad.squad.event;

/**
 * 会话提交后的读取提示
 * <p>
 * 仅传递用户及会话定位，订阅端自行读取已提交公开事件；不携带执行资源或模型上下文。
 */
public record ConversationCommittedSignal(Long userId, Long conversationId) {
}
