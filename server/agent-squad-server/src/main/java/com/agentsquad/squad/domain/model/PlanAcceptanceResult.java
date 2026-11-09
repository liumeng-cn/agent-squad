package com.agentsquad.squad.domain.model;

import com.agentsquad.squad.domain.enums.PlanAcceptanceVerdict;

import com.agentsquad.squad.domain.enums.AcceptanceVerificationType;

import java.util.List;

/**
 * 服务端核实后的完成条件结论
 * <p>
 * 检查人、证据与目标和计划版本共同保存，后续发布不能使用其他版本的检查结果。
 */
public record PlanAcceptanceResult(String criterionId, String text, boolean required,
                                   AcceptanceVerificationType verificationType,
                                   PlanAcceptanceVerdict verdict, String verifier, List<String> evidenceRefs,
                                   long planVersion, long goalRevision) {
}
