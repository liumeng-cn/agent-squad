package com.agentsquad.squad.model.vo;

/**
 * 目标公开读模型；与 OpenAPI GoalTask 字段对齐。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class TaskVO {

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("conversationId")
    private final String conversationId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("planVersion")
    private final long planVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("answerRevision")
    private final long answerRevision;

    @com.fasterxml.jackson.annotation.JsonProperty("cancelRequestedAt")
    private final String cancelRequestedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("blockedReason")
    private final String blockedReason;

    @com.fasterxml.jackson.annotation.JsonProperty("createdAt")
    private final String createdAt;

    @com.fasterxml.jackson.annotation.JsonProperty("updatedAt")
    private final String updatedAt;

    @com.fasterxml.jackson.annotation.JsonCreator
    public TaskVO(
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("conversationId") String conversationId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("planVersion") long planVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("answerRevision") long answerRevision,
            @com.fasterxml.jackson.annotation.JsonProperty("cancelRequestedAt") String cancelRequestedAt,
            @com.fasterxml.jackson.annotation.JsonProperty("blockedReason") String blockedReason,
            @com.fasterxml.jackson.annotation.JsonProperty("createdAt") String createdAt,
            @com.fasterxml.jackson.annotation.JsonProperty("updatedAt") String updatedAt) {
        this.taskId = taskId;
        this.conversationId = conversationId;
        this.status = status;
        this.version = version;
        this.planVersion = planVersion;
        this.answerRevision = answerRevision;
        this.cancelRequestedAt = cancelRequestedAt;
        this.blockedReason = blockedReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
