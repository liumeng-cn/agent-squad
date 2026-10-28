package com.agentsquad.squad.interaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 交互答复的传输结构。
 *
 * expectedVersion 对应交互版本，answer 的类型由服务端持久交互决定。
 */
public record InteractionAnswerRequest(@NotNull Long expectedVersion, @NotNull @Valid InteractionAnswer answer) {
}
