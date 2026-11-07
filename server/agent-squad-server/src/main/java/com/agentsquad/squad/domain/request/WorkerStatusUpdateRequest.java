package com.agentsquad.squad.domain.request;

import com.agentsquad.squad.common.enums.worker.WorkerDefinitionStatus;
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
