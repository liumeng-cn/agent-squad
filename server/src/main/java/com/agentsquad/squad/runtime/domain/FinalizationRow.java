package com.agentsquad.squad.runtime.domain;

/** 终态裁决时锁定的目标及候选快照。 */
public record FinalizationRow(String userId, String taskId, String conversationId,
                              String status, long version, String proposalBindingId, String proposalTurnId,
                              String proposalJson, boolean cancellationRequested) {
}
