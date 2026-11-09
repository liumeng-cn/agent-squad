package com.agentsquad.squad.model.vo;


import java.util.List;

/**
 * 统一消息正文视图。
 * <p>
 * 按会话序号排序并按 revision 替换；历史任务消息允许没有独立回复执行身份。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MessageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("messageId")
    private final String messageId;

    @com.fasterxml.jackson.annotation.JsonProperty("sequence")
    private final long sequence;

    @com.fasterxml.jackson.annotation.JsonProperty("role")
    private final String role;

    @com.fasterxml.jackson.annotation.JsonProperty("text")
    private final String text;

    @com.fasterxml.jackson.annotation.JsonProperty("revision")
    private final long revision;

    @com.fasterxml.jackson.annotation.JsonProperty("completed")
    private final boolean completed;

    @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId")
    private final String sourceMessageId;

    @com.fasterxml.jackson.annotation.JsonProperty("executionId")
    private final String executionId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("occurredAt")
    private final String occurredAt;

    @com.fasterxml.jackson.annotation.JsonProperty("artifacts")
    private final List<ArtifactVO> artifacts;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MessageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("messageId") String messageId,
            @com.fasterxml.jackson.annotation.JsonProperty("sequence") long sequence,
            @com.fasterxml.jackson.annotation.JsonProperty("role") String role,
            @com.fasterxml.jackson.annotation.JsonProperty("text") String text,
            @com.fasterxml.jackson.annotation.JsonProperty("revision") long revision,
            @com.fasterxml.jackson.annotation.JsonProperty("completed") boolean completed,
            @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId") String sourceMessageId,
            @com.fasterxml.jackson.annotation.JsonProperty("executionId") String executionId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("occurredAt") String occurredAt,
            @com.fasterxml.jackson.annotation.JsonProperty("artifacts") List<ArtifactVO> artifacts) {
        this.messageId = messageId;
        this.sequence = sequence;
        this.role = role;
        this.text = text;
        this.revision = revision;
        this.completed = completed;
        this.sourceMessageId = sourceMessageId;
        this.executionId = executionId;
        this.taskId = taskId;
        this.occurredAt = occurredAt;
        this.artifacts = artifacts;
    }
}
