package com.agentsquad.squad.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 提交目标请求；用户输入不会作为身份或框架 Session ID 使用。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class TaskCreateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("message")
    @NotBlank
    @Size(max = 20000)
    private final String message;

    @com.fasterxml.jackson.annotation.JsonProperty("artifactIds")
    @Size(max = 20)
    private final List<Long> artifactIds;

    @com.fasterxml.jackson.annotation.JsonProperty("profileKey")
    @Pattern(regexp = "STANDARD")
    private final String profileKey;

    @com.fasterxml.jackson.annotation.JsonCreator
    public TaskCreateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("message") String message,
            @com.fasterxml.jackson.annotation.JsonProperty("artifactIds") List<Long> artifactIds,
            @com.fasterxml.jackson.annotation.JsonProperty("profileKey") String profileKey) {
        this.message = message;
        this.artifactIds = artifactIds;
        this.profileKey = profileKey;
    }
}
