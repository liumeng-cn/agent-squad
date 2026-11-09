package com.agentsquad.squad.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Worker 能力版本发布请求。
 * <p>
 * 输入输出 Schema 及工具白名单成为不可变版本正文，不携带执行实例状态。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class WorkerDefinitionCreateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("workerKey")
    @NotBlank
    @Size(max = 80)
    private final String workerKey;

    @com.fasterxml.jackson.annotation.JsonProperty("workerVersion")
    @NotBlank
    @Size(max = 40)
    private final String workerVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("description")
    @NotBlank
    @Size(max = 4000)
    private final String description;

    @com.fasterxml.jackson.annotation.JsonProperty("inputSchema")
    @NotNull
    private final JsonNode inputSchema;

    @com.fasterxml.jackson.annotation.JsonProperty("outputSchema")
    @NotNull
    private final JsonNode outputSchema;

    @com.fasterxml.jackson.annotation.JsonProperty("toolAllowlist")
    @NotNull
    @Size(max = 64)
    private final List<@NotBlank @Size(max = 100) String> toolAllowlist;

    @com.fasterxml.jackson.annotation.JsonProperty("modelProfileKey")
    @NotBlank
    @Size(max = 80)
    private final String modelProfileKey;

    @com.fasterxml.jackson.annotation.JsonProperty("runtimeProfileKey")
    @NotBlank
    @Size(max = 80)
    private final String runtimeProfileKey;

    @com.fasterxml.jackson.annotation.JsonCreator
    public WorkerDefinitionCreateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("workerKey") String workerKey,
            @com.fasterxml.jackson.annotation.JsonProperty("workerVersion") String workerVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("description") String description,
            @com.fasterxml.jackson.annotation.JsonProperty("inputSchema") JsonNode inputSchema,
            @com.fasterxml.jackson.annotation.JsonProperty("outputSchema") JsonNode outputSchema,
            @com.fasterxml.jackson.annotation.JsonProperty("toolAllowlist") List<@NotBlank @Size(max = 100) String> toolAllowlist,
            @com.fasterxml.jackson.annotation.JsonProperty("modelProfileKey") String modelProfileKey,
            @com.fasterxml.jackson.annotation.JsonProperty("runtimeProfileKey") String runtimeProfileKey) {
        this.workerKey = workerKey;
        this.workerVersion = workerVersion;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.toolAllowlist = toolAllowlist;
        this.modelProfileKey = modelProfileKey;
        this.runtimeProfileKey = runtimeProfileKey;
    }
}
