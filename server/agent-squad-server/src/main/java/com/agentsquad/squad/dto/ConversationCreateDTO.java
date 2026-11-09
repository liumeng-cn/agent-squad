package com.agentsquad.squad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新对话请求。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConversationCreateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("title")
    @NotBlank
    @Size(max = 120)
    private final String title;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConversationCreateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("title") String title) {
        this.title = title;
    }
}
