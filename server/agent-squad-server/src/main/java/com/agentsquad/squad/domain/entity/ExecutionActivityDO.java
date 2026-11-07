package com.agentsquad.squad.domain.entity;

import com.agentsquad.squad.common.enums.execution.ActivityGroup;
import com.agentsquad.squad.common.enums.execution.ActivityStatus;
import com.agentsquad.squad.common.enums.execution.ActivityType;
import com.agentsquad.squad.common.enums.execution.ActivityWaitReason;

import java.time.LocalDateTime;

/**
 * 已提交执行活动记录
 * <p>
 * 固定原生调用与消息、计划节点归属，仅保存安全分类，不复制提示词、工具参数或工具结果。
 */
public record ExecutionActivityDO(String activityId, String userId, String scopeId, String conversationId,
                                   String sourceMessageId, String executionId, String taskId, String jobId,
                                   String bindingId,
                                   String runId, String turnId, String sourceKey, long planVersion, ActivityType type,
                                   ActivityGroup group,
                                   ActivityStatus status, ActivityWaitReason waitReason, String summary,
                                   LocalDateTime startedAt,
                                   LocalDateTime endedAt, long version, long nativeSequence) {
}
