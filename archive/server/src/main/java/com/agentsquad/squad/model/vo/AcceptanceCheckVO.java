package com.agentsquad.squad.model.vo;

import com.agentsquad.squad.enums.AcceptanceVerificationType;
import com.agentsquad.squad.enums.PlanAcceptanceVerdict;

/**
 * 已发布结果的逐项检查说明
 * <p>
 * 只公开条件、检查方式与结论，不暴露内部会话、原始证据内容或模型推理。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AcceptanceCheckVO {

    @com.fasterxml.jackson.annotation.JsonProperty("criterionId")
    private final String criterionId;

    @com.fasterxml.jackson.annotation.JsonProperty("text")
    private final String text;

    @com.fasterxml.jackson.annotation.JsonProperty("required")
    private final boolean required;

    @com.fasterxml.jackson.annotation.JsonProperty("verificationType")
    private final AcceptanceVerificationType verificationType;

    @com.fasterxml.jackson.annotation.JsonProperty("verdict")
    private final PlanAcceptanceVerdict verdict;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AcceptanceCheckVO(
            @com.fasterxml.jackson.annotation.JsonProperty("criterionId") String criterionId,
            @com.fasterxml.jackson.annotation.JsonProperty("text") String text,
            @com.fasterxml.jackson.annotation.JsonProperty("required") boolean required,
            @com.fasterxml.jackson.annotation.JsonProperty("verificationType") AcceptanceVerificationType verificationType,
            @com.fasterxml.jackson.annotation.JsonProperty("verdict") PlanAcceptanceVerdict verdict) {
        this.criterionId = criterionId;
        this.text = text;
        this.required = required;
        this.verificationType = verificationType;
        this.verdict = verdict;
    }
}
