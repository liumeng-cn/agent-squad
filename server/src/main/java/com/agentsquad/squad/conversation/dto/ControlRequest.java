package com.agentsquad.squad.conversation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 任务控制请求；携带期望版本以进行乐观并发校验。
 */
public record ControlRequest(@NotNull @Min(0) Long expectedVersion) {
}
