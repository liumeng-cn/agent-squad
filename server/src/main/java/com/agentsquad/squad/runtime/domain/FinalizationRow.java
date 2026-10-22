package com.agentsquad.squad.runtime.domain;

/**
 * 目标终态裁决快照。
 *
 * 包含候选、取消屏障和纠正次数，供同一事务判断是否完成、取消或纠正。
 */
public record FinalizationRow(String userId, String taskId, String conversationId,
                              String status, long version, long planVersion, long answerRevision,
                              String proposalBindingId, String proposalTurnId,
                              String proposalJson, boolean cancellationRequested, int correctionCount) {
}
