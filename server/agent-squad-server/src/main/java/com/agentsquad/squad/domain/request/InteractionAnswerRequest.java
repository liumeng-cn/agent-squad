package com.agentsquad.squad.domain.request;

import com.agentsquad.squad.domain.dto.InteractionAnswer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 交互答复的传输结构。
 *
 * expectedVersion 对应交互版本，answer 的类型由服务端持久交互决定。
 */
public record InteractionAnswerRequest(@NotNull Long expectedVersion, @NotNull @Valid InteractionAnswer answer) {
}
