package com.agentsquad.squad.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.agentsquad.squad.enums.WorkerJobStatus;
import com.agentsquad.squad.enums.WorkerSkipReason;

import java.util.List;

/**
 * Worker 任务公开视图；结果只在终态成功后呈现，候选和内部输入不公开。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class WorkerJobVO {

    @com.fasterxml.jackson.annotation.JsonProperty("jobId")
    private final String jobId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("nodeKey")
    private final String nodeKey;

    @com.fasterxml.jackson.annotation.JsonProperty("planVersion")
    private final long planVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("workerKey")
    private final String workerKey;

    @com.fasterxml.jackson.annotation.JsonProperty("workerVersion")
    private final String workerVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final WorkerJobStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("bindingId")
    private final String bindingId;

    @com.fasterxml.jackson.annotation.JsonProperty("reusedFromJobId")
    private final String reusedFromJobId;

    @com.fasterxml.jackson.annotation.JsonProperty("result")
    private final Object result;

    @com.fasterxml.jackson.annotation.JsonProperty("title")
    private final String title;

    @com.fasterxml.jackson.annotation.JsonProperty("dependsOn")
    private final List<String> dependsOn;

    @com.fasterxml.jackson.annotation.JsonProperty("skipReason")
    private final WorkerSkipReason skipReason;

    @com.fasterxml.jackson.annotation.JsonProperty("required")
    private final Boolean required;

    @com.fasterxml.jackson.annotation.JsonCreator
    public WorkerJobVO(
            @com.fasterxml.jackson.annotation.JsonProperty("jobId") String jobId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("nodeKey") String nodeKey,
            @com.fasterxml.jackson.annotation.JsonProperty("planVersion") long planVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("workerKey") String workerKey,
            @com.fasterxml.jackson.annotation.JsonProperty("workerVersion") String workerVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("status") WorkerJobStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("bindingId") String bindingId,
            @com.fasterxml.jackson.annotation.JsonProperty("reusedFromJobId") String reusedFromJobId,
            @com.fasterxml.jackson.annotation.JsonProperty("result") Object result,
            @com.fasterxml.jackson.annotation.JsonProperty("title") String title,
            @com.fasterxml.jackson.annotation.JsonProperty("dependsOn") List<String> dependsOn,
            @com.fasterxml.jackson.annotation.JsonProperty("skipReason") WorkerSkipReason skipReason,
            @com.fasterxml.jackson.annotation.JsonProperty("required") Boolean required) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.nodeKey = nodeKey;
        this.planVersion = planVersion;
        this.workerKey = workerKey;
        this.workerVersion = workerVersion;
        this.status = status;
        this.version = version;
        this.bindingId = bindingId;
        this.reusedFromJobId = reusedFromJobId;
        this.result = result;
        this.title = title;
        this.dependsOn = dependsOn;
        this.skipReason = skipReason;
        this.required = required;
    }
}
