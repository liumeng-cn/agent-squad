package com.agentsquad.squad.model.vo;

import com.agentsquad.squad.enums.ResponseExecutionStatus;

import java.util.List;

/**
 * 用户可观察的回复执行视图。
 * <p>
 * 状态与允许动作来自服务端事实，停止回答不会隐式终结关联目标。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ResponseExecutionVO {

    @com.fasterxml.jackson.annotation.JsonProperty("executionId")
    private final String executionId;

    @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId")
    private final String sourceMessageId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final ResponseExecutionStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("answerMessageId")
    private final String answerMessageId;

    @com.fasterxml.jackson.annotation.JsonProperty("failureId")
    private final String failureId;

    @com.fasterxml.jackson.annotation.JsonProperty("allowedActions")
    private final List<String> allowedActions;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ResponseExecutionVO(
            @com.fasterxml.jackson.annotation.JsonProperty("executionId") String executionId,
            @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId") String sourceMessageId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") ResponseExecutionStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("answerMessageId") String answerMessageId,
            @com.fasterxml.jackson.annotation.JsonProperty("failureId") String failureId,
            @com.fasterxml.jackson.annotation.JsonProperty("allowedActions") List<String> allowedActions) {
        this.executionId = executionId;
        this.sourceMessageId = sourceMessageId;
        this.status = status;
        this.version = version;
        this.answerMessageId = answerMessageId;
        this.failureId = failureId;
        this.allowedActions = allowedActions;
    }
}
