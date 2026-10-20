package com.agentsquad.squad.runtime.dto;

/**
 * 目标公开读模型。
 */
public record TaskResponse(String taskId, String conversationId, String status, long version,
                           String createdAt, String updatedAt) {
}
