package com.agentsquad.squad.model.dto;

import com.agentsquad.squad.enums.WorkerDefinitionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Worker 能力状态变更请求。
 * <p>
 * expectedVersion 防止并发启停或撤销覆盖另一次管理决定。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class WorkerStatusUpdateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion")
    @NotNull
    @PositiveOrZero
    private final Long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    @NotNull
    private final WorkerDefinitionStatus status;

    @com.fasterxml.jackson.annotation.JsonCreator
    public WorkerStatusUpdateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion") Long expectedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("status") WorkerDefinitionStatus status) {
        this.expectedVersion = expectedVersion;
        this.status = status;
    }
}
