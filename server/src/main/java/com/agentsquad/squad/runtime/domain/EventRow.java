package com.agentsquad.squad.runtime.domain;

import java.time.LocalDateTime;

/** 用户可见事件的持久层投影，按任务序号回放。 */
public record EventRow(long eventSeq, String taskId, String eventType, String payloadJson,
                       LocalDateTime occurredAt) {
}
