package com.agentsquad.squad.domain.entity;

/**
 * 回复冻结时观察到的安全故障事实。
 * <p>
 * 仅含同用户、同会话且来源消息不晚于本次消息的已提交事实，不携带内部异常或原始推理。
 */
public record ResponseFailureFactDO(String category, String actor, String safeMessage, String sourceMessageId,
                                  String taskId) {
}
