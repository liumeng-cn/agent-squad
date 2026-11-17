package com.agentsquad.squad.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

/**
 * 手动修改会话标题的请求。
 * <p>
 * 独立标题版本避免执行状态变化导致误覆盖，晚到的自动建议不能覆盖已手动命名的主题。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConversationTitleUpdateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("title")
    @NotBlank
    @Size(max = 200)
    private final String title;

    @com.fasterxml.jackson.annotation.JsonProperty("expectedTitleVersion")
    @jakarta.validation.constraints.NotNull
    @Min(0)
    private final Long expectedTitleVersion;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConversationTitleUpdateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("title") String title,
            @com.fasterxml.jackson.annotation.JsonProperty("expectedTitleVersion") Long expectedTitleVersion) {
        this.title = title;
        this.expectedTitleVersion = expectedTitleVersion;
    }
}
