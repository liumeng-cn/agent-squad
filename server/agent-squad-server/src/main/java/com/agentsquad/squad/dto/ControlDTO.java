package com.agentsquad.squad.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 任务控制请求；携带期望版本以进行乐观并发校验。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ControlDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion")
    @NotNull
    @Min(0)
    private final Long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ControlDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion") Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
