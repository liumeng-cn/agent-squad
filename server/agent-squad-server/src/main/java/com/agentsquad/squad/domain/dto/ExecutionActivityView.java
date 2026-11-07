package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.common.enums.execution.ActivityGroup;
import com.agentsquad.squad.common.enums.execution.ActivityStatus;
import com.agentsquad.squad.common.enums.execution.ActivityType;
import com.agentsquad.squad.common.enums.execution.ActivityWaitReason;
import com.agentsquad.squad.domain.entity.ExecutionActivityDO;

import java.time.ZoneOffset;

/**
 * 用户可见的真实执行活动
 * <p>
 * 仅包含归属、分类、状态与可信起止时间；缺少开始或结束事实时保持空值，不推算虚假耗时。
 */
public record ExecutionActivityView(String activityId, String sourceMessageId, String executionId,
                                    String taskId, String jobId, long planVersion, ActivityType type,
                                    ActivityGroup group,
                                    ActivityStatus status, ActivityWaitReason waitReason, String summary,
                                    String startedAt,
                                    String endedAt, long version) {
    public static ExecutionActivityView from(ExecutionActivityDO row) {
        return new ExecutionActivityView(row.activityId(), row.sourceMessageId(), row.executionId(), row.taskId(),
                row.jobId(), row.planVersion(), row.type(), row.group(), row.status(), row.waitReason(), row.summary(),
                row.startedAt() == null ? null : row.startedAt().atOffset(ZoneOffset.UTC).toString(),
                row.endedAt() == null ? null : row.endedAt().atOffset(ZoneOffset.UTC).toString(), row.version());
    }
}
