package com.agentsquad.squad.model.dto;

import com.agentsquad.squad.enums.ReasoningMode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户消息提交请求。
 * <p>
 * 只接受正文与就绪附件引用；用户、执行作用域和任务由服务端决定。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MessageCreateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("text")
    @NotBlank
    @Size(max = 20000)
    private final String text;

    @com.fasterxml.jackson.annotation.JsonProperty("artifactIds")
    @Size(max = 20)
    private final List<@NotBlank String> artifactIds;

    @com.fasterxml.jackson.annotation.JsonProperty("modelId")
    @Size(max = 100)
    private final String modelId;

    @com.fasterxml.jackson.annotation.JsonProperty("reasoningMode")
    private final ReasoningMode reasoningMode;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MessageCreateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("text") String text,
            @com.fasterxml.jackson.annotation.JsonProperty("artifactIds") List<@NotBlank String> artifactIds,
            @com.fasterxml.jackson.annotation.JsonProperty("modelId") String modelId,
            @com.fasterxml.jackson.annotation.JsonProperty("reasoningMode") ReasoningMode reasoningMode) {
        this.text = text;
        this.artifactIds = artifactIds;
        this.modelId = modelId;
        this.reasoningMode = reasoningMode;
    }

    public MessageCreateDTO(String text, List<String> artifactIds) {
        this(text, artifactIds, null, null);
    }

    /**
     * 消息入口不接受调用方注入用户、任务或执行身份；未知字段按传输契约拒绝。
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("消息请求包含不支持的字段");
    }
}
