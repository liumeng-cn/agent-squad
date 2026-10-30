package com.agentsquad.squad.conversation.domain;

/**
 * 目标修订的持久投影。
 * <p>
 * 来源原文与Master归纳分开；指令终态不可原位复活，协调命令身份固定。
 */
public record TaskDirectiveRow(String directiveId, String userId, String conversationId, long generation,
                               String sourceMessageId, String sourceScopeId, String taskId, String logicalKey,
                               String payloadHash, long expectedVersion, long expectedGoalRevision, String kind,
                               String content, String sourceText, String artifactIdsJson, String status,
                               String commandId, Long appliedVersion, Long appliedGoalRevision, String reasonCode,
                               String reasonMessage) {
}
