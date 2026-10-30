package com.agentsquad.squad.runtime.domain;

/**
 * 有已投递命令的固定原生会话及当前持久投影水位。
 */
public record ProjectionTarget(String userId, String taskId, String bindingId,
                               String agentId, String sessionId, long cursor) {
}
