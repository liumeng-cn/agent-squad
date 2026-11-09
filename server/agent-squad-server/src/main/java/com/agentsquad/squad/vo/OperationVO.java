package com.agentsquad.squad.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 操作账本的公开摘要。
 *
 * 列表不返回执行参数、原生会话或连接凭据，详细核对由单独的受权视图提供。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class OperationVO {

    @com.fasterxml.jackson.annotation.JsonProperty("operationId")
    private final String operationId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("jobId")
    private final String jobId;

    @com.fasterxml.jackson.annotation.JsonProperty("toolKey")
    private final String toolKey;

    @com.fasterxml.jackson.annotation.JsonProperty("effect")
    private final String effect;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("requestHash")
    private final String requestHash;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("summary")
    private final String summary;

    @com.fasterxml.jackson.annotation.JsonCreator
    public OperationVO(
            @com.fasterxml.jackson.annotation.JsonProperty("operationId") String operationId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("jobId") String jobId,
            @com.fasterxml.jackson.annotation.JsonProperty("toolKey") String toolKey,
            @com.fasterxml.jackson.annotation.JsonProperty("effect") String effect,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("requestHash") String requestHash,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("summary") String summary) {
        this.operationId = operationId;
        this.taskId = taskId;
        this.jobId = jobId;
        this.toolKey = toolKey;
        this.effect = effect;
        this.status = status;
        this.requestHash = requestHash;
        this.version = version;
        this.summary = summary;
    }
}
