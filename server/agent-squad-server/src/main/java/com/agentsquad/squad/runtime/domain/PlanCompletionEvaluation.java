package com.agentsquad.squad.runtime.domain;

import java.util.List;

/**
 * 当前计划的完成检查结果
 * <p>
 * 汇总判断与逐项检查证据一起返回，由调用方在同一事务保存完成候选或决定拒绝。
 */
public record PlanCompletionEvaluation(PlanJoinDecision decision, List<PlanAcceptanceResult> acceptanceResults) {
}
