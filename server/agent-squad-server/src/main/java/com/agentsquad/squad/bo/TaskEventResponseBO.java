package com.agentsquad.squad.bo;

import java.time.LocalDateTime;

/**
 * 公开事件回放项。
 */
public record TaskEventResponseBO(long sequence, String taskId, String type, String payloadJson,
                                LocalDateTime occurredAt) {
}
