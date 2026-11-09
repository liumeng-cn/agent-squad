package com.agentsquad.squad.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 交互答复的传输结构。
 *
 * expectedVersion 对应交互版本，answer 的类型由服务端持久交互决定。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class InteractionAnswerDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion")
    @NotNull
    private final Long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("answer")
    @NotNull
    @Valid
    private final InteractionAnswerValueDTO answer;

    @com.fasterxml.jackson.annotation.JsonCreator
    public InteractionAnswerDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion") Long expectedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("answer") InteractionAnswerValueDTO answer) {
        this.expectedVersion = expectedVersion;
        this.answer = answer;
    }
}
