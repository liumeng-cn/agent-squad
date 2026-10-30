package com.agentsquad.squad.conversation.domain;

/**
 * 回复冻结时观察到的最近目标状态事实。
 * <p>
 * 仅含同用户、同会话且来源消息不晚于本次消息的已提交事实，不携带内部异常或原始推理。
 */
public record ResponseTaskStatusFact(String taskId, String sourceMessageId, String status, long version,
                                     long goalRevision) {
}
