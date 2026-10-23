package com.agentsquad.squad.tools.operation.dto;

/**
 * 操作账本对外视图；arguments/result 已解析为 JSON，供操作详情与核对页使用。
 */
public record OperationView(String operationId, String taskId, String bindingId,
                            String frameworkTurnId, String toolCallId, String toolKey,
                            String toolVersion, String businessIntentKey, String effect,
                            String connectionId, long connectionRevision, String requestHash,
                            Object arguments, String status, long version, String externalKey,
                            Object result) {
}
