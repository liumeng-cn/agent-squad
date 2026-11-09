package com.agentsquad.squad.model.vo;

import java.util.List;

/**
 * 面向用户的执行故障说明。
 * <p>
 * 仅公开安全原因、处理责任和允许动作；不承载堆栈、凭据或原生模型上下文。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ExecutionFailureVO {

    @com.fasterxml.jackson.annotation.JsonProperty("failureId")
    private final String failureId;

    @com.fasterxml.jackson.annotation.JsonProperty("scopeId")
    private final String scopeId;

    @com.fasterxml.jackson.annotation.JsonProperty("category")
    private final String category;

    @com.fasterxml.jackson.annotation.JsonProperty("code")
    private final String code;

    @com.fasterxml.jackson.annotation.JsonProperty("actor")
    private final String actor;

    @com.fasterxml.jackson.annotation.JsonProperty("safeMessage")
    private final String safeMessage;

    @com.fasterxml.jackson.annotation.JsonProperty("retryable")
    private final boolean retryable;

    @com.fasterxml.jackson.annotation.JsonProperty("attempt")
    private final int attempt;

    @com.fasterxml.jackson.annotation.JsonProperty("maxAttempts")
    private final int maxAttempts;

    @com.fasterxml.jackson.annotation.JsonProperty("diagnosticId")
    private final String diagnosticId;

    @com.fasterxml.jackson.annotation.JsonProperty("allowedActions")
    private final List<String> allowedActions;

    @com.fasterxml.jackson.annotation.JsonProperty("executionId")
    private final String executionId;

    @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion")
    private final Long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId")
    private final String sourceMessageId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("nextAttemptAt")
    private final String nextAttemptAt;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ExecutionFailureVO(
            @com.fasterxml.jackson.annotation.JsonProperty("failureId") String failureId,
            @com.fasterxml.jackson.annotation.JsonProperty("scopeId") String scopeId,
            @com.fasterxml.jackson.annotation.JsonProperty("category") String category,
            @com.fasterxml.jackson.annotation.JsonProperty("code") String code,
            @com.fasterxml.jackson.annotation.JsonProperty("actor") String actor,
            @com.fasterxml.jackson.annotation.JsonProperty("safeMessage") String safeMessage,
            @com.fasterxml.jackson.annotation.JsonProperty("retryable") boolean retryable,
            @com.fasterxml.jackson.annotation.JsonProperty("attempt") int attempt,
            @com.fasterxml.jackson.annotation.JsonProperty("maxAttempts") int maxAttempts,
            @com.fasterxml.jackson.annotation.JsonProperty("diagnosticId") String diagnosticId,
            @com.fasterxml.jackson.annotation.JsonProperty("allowedActions") List<String> allowedActions,
            @com.fasterxml.jackson.annotation.JsonProperty("executionId") String executionId,
            @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion") Long expectedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId") String sourceMessageId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("nextAttemptAt") String nextAttemptAt) {
        this.failureId = failureId;
        this.scopeId = scopeId;
        this.category = category;
        this.code = code;
        this.actor = actor;
        this.safeMessage = safeMessage;
        this.retryable = retryable;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.diagnosticId = diagnosticId;
        this.allowedActions = allowedActions;
        this.executionId = executionId;
        this.expectedVersion = expectedVersion;
        this.taskId = taskId;
        this.sourceMessageId = sourceMessageId;
        this.status = status;
        this.nextAttemptAt = nextAttemptAt;
    }
}
