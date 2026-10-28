package com.agentsquad.squad.conversation.dto;

/**
 * Master创建独立目标的业务请求。
 * <p>
 * 不接受用户、会话、预算或绑定参数；来源消息必须与当前回复的受权消息一致。
 */
public record GoalTaskCreate(String logicalKey, String title, String objective, String sourceMessageId) {
}
