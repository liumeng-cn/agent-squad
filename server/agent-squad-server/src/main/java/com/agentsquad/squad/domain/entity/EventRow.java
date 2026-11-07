package com.agentsquad.squad.domain.entity;

import java.time.LocalDateTime;

/**
 * 用户可见事件的数据库查询结果，按任务序号回放
 */
public record EventRow(long eventSeq, String taskId, String eventType, String payloadJson,
                       LocalDateTime occurredAt) {
}
