package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.enums.PlanAcceptanceVerdict;

import com.agentsquad.squad.enums.AcceptanceVerificationType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 服务端核实后的完成条件结论
 * <p>
 * 检查人、证据与目标和计划版本共同保存，后续发布不能使用其他版本的检查结果。
 */
public record PlanAcceptanceResultBO(String criterionId, String text, boolean required,
                                     AcceptanceVerificationType verificationType,
                                     PlanAcceptanceVerdict verdict, String verifier,
                                     @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> evidenceRefs,
                                     long planVersion, long goalRevision) {
}
