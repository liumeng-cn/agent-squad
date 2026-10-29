package com.agentsquad.squad.conversation.dto;

/**
 * 目标修订受理参数。
 * <p>
 * 用户、会话和来源消息由SDK绑定推导；expectedVersion必须匹配当前目标。
 */
public record TaskDirectiveSubmit(String logicalKey, String taskId, long expectedVersion, String kind, String content) {
}
