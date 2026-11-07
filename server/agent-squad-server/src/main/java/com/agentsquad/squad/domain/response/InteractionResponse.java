package com.agentsquad.squad.domain.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 待用户/审批处理的交互项；D1 直接回答阶段无交互项。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InteractionResponse(String interactionId, String taskId, String jobId,
                          String kind, String origin, String question,
                          Object answerSchema, String requestHash, long planVersion,
                          String expiresAt, String status, long version,
                          Object approvalContext) {
}
