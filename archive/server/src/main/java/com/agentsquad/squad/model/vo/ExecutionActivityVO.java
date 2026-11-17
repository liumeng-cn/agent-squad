package com.agentsquad.squad.model.vo;

import com.agentsquad.squad.enums.ActivityGroup;
import com.agentsquad.squad.enums.ActivityStatus;
import com.agentsquad.squad.enums.ActivityType;
import com.agentsquad.squad.enums.ActivityWaitReason;
import com.agentsquad.squad.model.po.ExecutionActivityPO;

import java.time.ZoneOffset;

/**
 * 用户可见的真实执行活动
 * <p>
 * 仅包含归属、分类、状态与可信起止时间；缺少开始或结束事实时保持空值，不推算虚假耗时。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ExecutionActivityVO {

    @com.fasterxml.jackson.annotation.JsonProperty("activityId")
    private final Long activityId;

    @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId")
    private final Long sourceMessageId;

    @com.fasterxml.jackson.annotation.JsonProperty("executionId")
    private final Long executionId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final Long taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("jobId")
    private final Long jobId;

    @com.fasterxml.jackson.annotation.JsonProperty("planVersion")
    private final long planVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("type")
    private final ActivityType type;

    @com.fasterxml.jackson.annotation.JsonProperty("group")
    private final ActivityGroup group;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final ActivityStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("waitReason")
    private final ActivityWaitReason waitReason;

    @com.fasterxml.jackson.annotation.JsonProperty("summary")
    private final String summary;

    @com.fasterxml.jackson.annotation.JsonProperty("startedAt")
    private final String startedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("endedAt")
    private final String endedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ExecutionActivityVO(
            @com.fasterxml.jackson.annotation.JsonProperty("activityId") Long activityId,
            @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId") Long sourceMessageId,
            @com.fasterxml.jackson.annotation.JsonProperty("executionId") Long executionId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") Long taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("jobId") Long jobId,
            @com.fasterxml.jackson.annotation.JsonProperty("planVersion") long planVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("type") ActivityType type,
            @com.fasterxml.jackson.annotation.JsonProperty("group") ActivityGroup group,
            @com.fasterxml.jackson.annotation.JsonProperty("status") ActivityStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("waitReason") ActivityWaitReason waitReason,
            @com.fasterxml.jackson.annotation.JsonProperty("summary") String summary,
            @com.fasterxml.jackson.annotation.JsonProperty("startedAt") String startedAt,
            @com.fasterxml.jackson.annotation.JsonProperty("endedAt") String endedAt,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version) {
        this.activityId = activityId;
        this.sourceMessageId = sourceMessageId;
        this.executionId = executionId;
        this.taskId = taskId;
        this.jobId = jobId;
        this.planVersion = planVersion;
        this.type = type;
        this.group = group;
        this.status = status;
        this.waitReason = waitReason;
        this.summary = summary;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.version = version;
    }

    public static ExecutionActivityVO from(ExecutionActivityPO row) {
        return new ExecutionActivityVO(row.getActivityId(), row.getSourceMessageId(), row.getExecutionId(), row.getTaskId(),
                row.getJobId(), row.getPlanVersion(), row.getType(), row.getGroup(), row.getStatus(), row.getWaitReason(), row.getSummary(),
                row.getStartedAt() == null ? null : row.getStartedAt().atOffset(ZoneOffset.UTC).toString(),
                row.getEndedAt() == null ? null : row.getEndedAt().atOffset(ZoneOffset.UTC).toString(), row.getVersion());
    }
}
