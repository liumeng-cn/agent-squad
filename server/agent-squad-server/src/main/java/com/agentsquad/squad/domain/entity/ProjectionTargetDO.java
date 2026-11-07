package com.agentsquad.squad.domain.entity;

/**
 * 有已投递命令的固定原生会话及当前数据库记录水位
 */
public record ProjectionTargetDO(String userId, String taskId, String bindingId,
                               String agentId, String sessionId, long cursor) {
}
