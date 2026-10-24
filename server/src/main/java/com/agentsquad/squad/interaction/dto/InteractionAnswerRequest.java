package com.agentsquad.squad.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 交互答复请求体；kind 区分 APPROVAL（approve + confirmedRequestHash）与 INPUT（value 对象）。
 */
public record InteractionAnswerRequest(@NotNull Long expectedVersion,
                                       @NotBlank String kind,
                                       Boolean approve,
                                       String confirmedRequestHash,
                                       Map<String, Object> value) {
}
