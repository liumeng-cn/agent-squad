package com.agentsquad.squad.conversation.domain;

/**
 * 已领取命令的不可变投递快照；Binding 标识必须与命令记录一致。
 */
public record DeliveryRow(String commandId, String userId, String taskId, String bindingId,
                          String action, String payloadJson, int attempts, String agentId, String sessionId) {
}
