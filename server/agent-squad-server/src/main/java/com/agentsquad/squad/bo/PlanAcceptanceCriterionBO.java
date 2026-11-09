package com.agentsquad.squad.bo;

import com.agentsquad.squad.enums.AcceptanceVerificationType;
import com.agentsquad.squad.enums.DeterministicAcceptanceCheck;
import com.agentsquad.squad.utils.ContentDigests;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 计划中的完成条件
 * <p>
 * 条件身份随不可变计划保存；旧文字条件明确标为未结构化，不能倒推历史检查已经通过。
 */
public record PlanAcceptanceCriterionBO(String criterionId, String text, boolean required,
                                      AcceptanceVerificationType verificationType, boolean legacy,
                                      DeterministicAcceptanceCheck deterministicCheck) {
    public static PlanAcceptanceCriterionBO legacy(String text) {
        return new PlanAcceptanceCriterionBO("legacy-" + ContentDigests.sha256(text).substring(0, 24), text,
                true, AcceptanceVerificationType.MODEL_REVIEW, true, null);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PlanAcceptanceCriterionBO fromJson(JsonNode value) {
        if (value != null && value.isTextual()) {
            return legacy(value.asText());
        }
        if (value == null || !value.isObject() || !value.path("required").isBoolean()
                || !value.path("criterionId").isTextual() || !value.path("text").isTextual()
                || !value.path("verificationType").isTextual()) {
            throw new IllegalArgumentException("完成条件结构无效");
        }
        return new PlanAcceptanceCriterionBO(value.get("criterionId").asText(), value.get("text").asText(),
                value.get("required").asBoolean(), AcceptanceVerificationType.valueOf(value.get("verificationType").asText()),
                value.path("legacy").asBoolean(false), value.path("deterministicCheck").isTextual()
                ? DeterministicAcceptanceCheck.valueOf(value.get("deterministicCheck").asText()) : null);
    }
}
