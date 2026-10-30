package com.agentsquad.squad.conversation.dto;

/**
 * 目标公开读模型；与 OpenAPI GoalTask 字段对齐。
 */
public record TaskResponse(String taskId, String conversationId, String status, long version,
                           long planVersion, long answerRevision,
                           String cancelRequestedAt, String blockedReason,
                           String createdAt, String updatedAt) {
}
