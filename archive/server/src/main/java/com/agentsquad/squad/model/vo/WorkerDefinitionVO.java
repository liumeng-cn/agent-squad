package com.agentsquad.squad.model.vo;

import com.agentsquad.squad.enums.WorkerDefinitionStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Worker 能力定义公开视图。
 * <p>
 * 展示不可变定义、当前可调度状态和状态乐观版本。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class WorkerDefinitionVO {

    @com.fasterxml.jackson.annotation.JsonProperty("definitionId")
    private final Long definitionId;

    @com.fasterxml.jackson.annotation.JsonProperty("workerKey")
    private final String workerKey;

    @com.fasterxml.jackson.annotation.JsonProperty("workerVersion")
    private final String workerVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("description")
    private final String description;

    @com.fasterxml.jackson.annotation.JsonProperty("inputSchema")
    private final JsonNode inputSchema;

    @com.fasterxml.jackson.annotation.JsonProperty("outputSchema")
    private final JsonNode outputSchema;

    @com.fasterxml.jackson.annotation.JsonProperty("toolAllowlist")
    private final List<String> toolAllowlist;

    @com.fasterxml.jackson.annotation.JsonProperty("modelProfileKey")
    private final String modelProfileKey;

    @com.fasterxml.jackson.annotation.JsonProperty("runtimeProfileKey")
    private final String runtimeProfileKey;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final WorkerDefinitionStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonCreator
    public WorkerDefinitionVO(
            @com.fasterxml.jackson.annotation.JsonProperty("definitionId") Long definitionId,
            @com.fasterxml.jackson.annotation.JsonProperty("workerKey") String workerKey,
            @com.fasterxml.jackson.annotation.JsonProperty("workerVersion") String workerVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("description") String description,
            @com.fasterxml.jackson.annotation.JsonProperty("inputSchema") JsonNode inputSchema,
            @com.fasterxml.jackson.annotation.JsonProperty("outputSchema") JsonNode outputSchema,
            @com.fasterxml.jackson.annotation.JsonProperty("toolAllowlist") List<String> toolAllowlist,
            @com.fasterxml.jackson.annotation.JsonProperty("modelProfileKey") String modelProfileKey,
            @com.fasterxml.jackson.annotation.JsonProperty("runtimeProfileKey") String runtimeProfileKey,
            @com.fasterxml.jackson.annotation.JsonProperty("status") WorkerDefinitionStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version) {
        this.definitionId = definitionId;
        this.workerKey = workerKey;
        this.workerVersion = workerVersion;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.toolAllowlist = toolAllowlist;
        this.modelProfileKey = modelProfileKey;
        this.runtimeProfileKey = runtimeProfileKey;
        this.status = status;
        this.version = version;
    }
}
