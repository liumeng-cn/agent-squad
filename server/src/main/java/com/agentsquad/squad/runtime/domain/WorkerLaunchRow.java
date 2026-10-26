package com.agentsquad.squad.runtime.domain;

import java.time.LocalDateTime;

/**
 * Worker 固定 Binding 对应的计划、节点与业务 guard 快照。
 */
public record WorkerLaunchRow(String userId, String taskId, String jobId, long planVersion,
                              String nodeKey, String workerKey, String workerVersion,
                              String jobStatus, String planStatus, String bindingId,
                              String agentId, String inputJson, LocalDateTime cancelRequestedAt,
                              String guardToken, LocalDateTime guardLeaseUntil,
                              String resultCandidateJson, String candidateGuardToken,
                              String candidateTurnId) {
}
