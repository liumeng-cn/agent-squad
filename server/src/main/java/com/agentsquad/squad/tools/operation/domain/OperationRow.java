package com.agentsquad.squad.tools.operation.domain;

/**
 * 外部操作的持久层投影；账本是副作用唯一权威，框架日志只是佐证。
 */
public record OperationRow(String operationId, String userId, String taskId, String jobId,
                           String bindingId, String frameworkTurnId, String toolCallId,
                           String toolKey, String toolVersion, String businessIntentKey,
                           String effect, String connectionId, long connectionRevision,
                           long authVersion, String requestHash, String argumentsJson,
                           String status, long version, String executorOwner, long executorFence,
                           String externalKey, String resultJson) {
}
