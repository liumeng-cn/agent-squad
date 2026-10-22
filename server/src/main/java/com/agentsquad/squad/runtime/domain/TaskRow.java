package com.agentsquad.squad.runtime.domain;

import java.time.LocalDateTime;

/**
 * 用户目标持久层投影；原生 Turn 和 Run 不属于此对象。
 */
public record TaskRow(long id, String taskId, String userId, String conversationId,
                      String status, long version, long planVersion, long answerRevision,
                      LocalDateTime cancelRequestedAt, String blockedReason,
                      LocalDateTime createTime, LocalDateTime lastUpdateTime) {
}
