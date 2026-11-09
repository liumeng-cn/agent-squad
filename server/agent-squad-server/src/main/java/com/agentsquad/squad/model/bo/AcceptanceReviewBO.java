package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.enums.PlanAcceptanceVerdict;

import java.util.List;

/**
 * 模型提交的逐项检查意见
 * <p>
 * 只是一项待核实声明；服务端检查证据归属、计划版本和检查方式后才保存正式结论。
 */
public record AcceptanceReviewBO(String criterionId, PlanAcceptanceVerdict verdict, List<String> evidenceRefs) {
}
