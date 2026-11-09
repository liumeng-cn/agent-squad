package com.agentsquad.squad.vo;

import com.agentsquad.squad.enums.TaskDirectiveStatus;
import com.agentsquad.squad.enums.TaskDirectiveWaitReason;
import com.agentsquad.squad.po.TaskDirectivePO;

/**
 * 用户要求修改的公开处理结果
 * <p>
 * 只公开来源、等待原因和实际应用版本，不包含模型归纳、原文材料及内部命令。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class TaskDirectiveVO {

    @com.fasterxml.jackson.annotation.JsonProperty("directiveId")
    private final String directiveId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId")
    private final String sourceMessageId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final TaskDirectiveStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("waitReason")
    private final TaskDirectiveWaitReason waitReason;

    @com.fasterxml.jackson.annotation.JsonProperty("appliedVersion")
    private final Long appliedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("goalRevision")
    private final Long goalRevision;

    @com.fasterxml.jackson.annotation.JsonProperty("reasonCode")
    private final String reasonCode;

    @com.fasterxml.jackson.annotation.JsonProperty("reasonMessage")
    private final String reasonMessage;

    @com.fasterxml.jackson.annotation.JsonCreator
    public TaskDirectiveVO(
            @com.fasterxml.jackson.annotation.JsonProperty("directiveId") String directiveId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId") String sourceMessageId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") TaskDirectiveStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("waitReason") TaskDirectiveWaitReason waitReason,
            @com.fasterxml.jackson.annotation.JsonProperty("appliedVersion") Long appliedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("goalRevision") Long goalRevision,
            @com.fasterxml.jackson.annotation.JsonProperty("reasonCode") String reasonCode,
            @com.fasterxml.jackson.annotation.JsonProperty("reasonMessage") String reasonMessage) {
        this.directiveId = directiveId;
        this.taskId = taskId;
        this.sourceMessageId = sourceMessageId;
        this.status = status;
        this.waitReason = waitReason;
        this.appliedVersion = appliedVersion;
        this.goalRevision = goalRevision;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
    }
    /**
     * 已有历史受理记录没有等待原因时，保守显示等待安全处理点，不推测已经开始协调。
     */
    public static TaskDirectiveVO from(TaskDirectivePO row) {
        TaskDirectiveStatus status = TaskDirectiveStatus.valueOf(row.getStatus());
        TaskDirectiveWaitReason wait = TaskDirectiveWaitReason.NONE;
        if (status == TaskDirectiveStatus.ACCEPTED) {
            wait = TaskDirectiveWaitReason.WAITING_SAFE_POINT;
            if (row.getReasonCode() != null) {
                try {
                    wait = TaskDirectiveWaitReason.valueOf(row.getReasonCode());
                } catch (IllegalArgumentException unknown) {
                    // 旧错误码不能伪造成已开始协调。
                }
            }
        }
        return new TaskDirectiveVO(row.getDirectiveId(), row.getTaskId(), row.getSourceMessageId(), status, wait,
                row.getAppliedVersion(), row.getAppliedGoalRevision(), row.getReasonCode(), row.getReasonMessage());
    }
}
