package com.agentsquad.squad.runtime.domain;

/**
 * 待投递命令及幂等收据的持久层投影。
 */
public record CommandRow(String commandId, String userId, String taskId, String bindingId,
                         String payloadHash, String status, String nativeTurnId,
                         String nativeRunId, String nativeStatus) {
}
