package com.agentsquad.squad.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 追问或审批的类型化答复。
 * <p>
 * 服务层按持久交互类型核验字段，审批确认摘要不能由浏览器编辑。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class InteractionAnswerValueDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("kind")
    @NotBlank
    private final String kind;

    @com.fasterxml.jackson.annotation.JsonProperty("approve")
    private final Boolean approve;

    @com.fasterxml.jackson.annotation.JsonProperty("confirmedRequestHash")
    private final String confirmedRequestHash;

    @com.fasterxml.jackson.annotation.JsonProperty("value")
    private final Map<String, Object> value;

    @com.fasterxml.jackson.annotation.JsonCreator
    public InteractionAnswerValueDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("kind") String kind,
            @com.fasterxml.jackson.annotation.JsonProperty("approve") Boolean approve,
            @com.fasterxml.jackson.annotation.JsonProperty("confirmedRequestHash") String confirmedRequestHash,
            @com.fasterxml.jackson.annotation.JsonProperty("value") Map<String, Object> value) {
        this.kind = kind;
        this.approve = approve;
        this.confirmedRequestHash = confirmedRequestHash;
        this.value = value;
    }
}
