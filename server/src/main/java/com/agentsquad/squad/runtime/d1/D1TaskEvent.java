package com.agentsquad.squad.runtime.d1;

import java.time.Instant;

/** D1 任务公开事件，后续由 sq_task_event 投影提供游标读取。 */
public record D1TaskEvent(long sequence, String taskId, String type, String summary, Instant occurredAt) {
}
