package com.agentsquad.squad.conversation.domain;

/**
 * 待投递命令及幂等收据的数据库查询结果
 */
public record CommandRow(String commandId, String userId, String taskId, String bindingId,
                         String payloadHash, String status, String nativeTurnId,
                         String nativeRunId, String nativeStatus) {
}
