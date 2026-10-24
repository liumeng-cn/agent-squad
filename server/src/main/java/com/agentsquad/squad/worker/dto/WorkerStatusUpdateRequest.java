package com.agentsquad.squad.worker.dto;

import com.agentsquad.squad.worker.domain.WorkerDefinitionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Worker 能力状态变更请求。
 * <p>
 * expectedVersion 防止并发启停或撤销覆盖另一次管理决定。
 */
public record WorkerStatusUpdateRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull WorkerDefinitionStatus status) {
}
