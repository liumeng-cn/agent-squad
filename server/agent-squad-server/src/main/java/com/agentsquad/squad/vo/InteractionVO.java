package com.agentsquad.squad.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 待用户/审批处理的交互项；D1 直接回答阶段无交互项。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class InteractionVO {

    @com.fasterxml.jackson.annotation.JsonProperty("interactionId")
    private final String interactionId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("jobId")
    private final String jobId;

    @com.fasterxml.jackson.annotation.JsonProperty("kind")
    private final String kind;

    @com.fasterxml.jackson.annotation.JsonProperty("origin")
    private final String origin;

    @com.fasterxml.jackson.annotation.JsonProperty("question")
    private final String question;

    @com.fasterxml.jackson.annotation.JsonProperty("answerSchema")
    private final Object answerSchema;

    @com.fasterxml.jackson.annotation.JsonProperty("requestHash")
    private final String requestHash;

    @com.fasterxml.jackson.annotation.JsonProperty("planVersion")
    private final long planVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
    private final String expiresAt;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("approvalContext")
    private final Object approvalContext;

    @com.fasterxml.jackson.annotation.JsonCreator
    public InteractionVO(
            @com.fasterxml.jackson.annotation.JsonProperty("interactionId") String interactionId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("jobId") String jobId,
            @com.fasterxml.jackson.annotation.JsonProperty("kind") String kind,
            @com.fasterxml.jackson.annotation.JsonProperty("origin") String origin,
            @com.fasterxml.jackson.annotation.JsonProperty("question") String question,
            @com.fasterxml.jackson.annotation.JsonProperty("answerSchema") Object answerSchema,
            @com.fasterxml.jackson.annotation.JsonProperty("requestHash") String requestHash,
            @com.fasterxml.jackson.annotation.JsonProperty("planVersion") long planVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("expiresAt") String expiresAt,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("approvalContext") Object approvalContext) {
        this.interactionId = interactionId;
        this.taskId = taskId;
        this.jobId = jobId;
        this.kind = kind;
        this.origin = origin;
        this.question = question;
        this.answerSchema = answerSchema;
        this.requestHash = requestHash;
        this.planVersion = planVersion;
        this.expiresAt = expiresAt;
        this.status = status;
        this.version = version;
        this.approvalContext = approvalContext;
    }
}
