package com.agentsquad.squad.interaction.domain;

import java.time.LocalDateTime;

/**
 * 持久交互的存储投影；APPROVAL 绑定操作账本，INPUT 只绑定原生请求。
 */
public record InteractionRow(String interactionId, String userId, String taskId, String jobId,
                             String bindingId, String operationId, String origin,
                             String frameworkRequestId, String kind, String question,
                             String answerSchemaJson, long planVersion, String requestHash,
                             long connectionRevision, LocalDateTime expiresAt, String status,
                             long version, String responseJson) {
}
