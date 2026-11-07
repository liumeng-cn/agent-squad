package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.common.enums.worker.AcceptanceVerificationType;
import com.agentsquad.squad.common.enums.worker.PlanAcceptanceVerdict;

/**
 * 已发布结果的逐项检查说明
 * <p>
 * 只公开条件、检查方式与结论，不暴露内部会话、原始证据内容或模型推理。
 */
public record AcceptanceCheckView(String criterionId, String text, boolean required,
                                  AcceptanceVerificationType verificationType, PlanAcceptanceVerdict verdict) {
}
