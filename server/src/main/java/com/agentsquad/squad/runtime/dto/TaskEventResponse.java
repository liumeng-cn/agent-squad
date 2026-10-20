package com.agentsquad.squad.runtime.dto;

import java.time.LocalDateTime;

/**
 * 公开事件回放项。
 */
public record TaskEventResponse(long sequence, String taskId, String type, String payloadJson,
                                LocalDateTime occurredAt) {
}
