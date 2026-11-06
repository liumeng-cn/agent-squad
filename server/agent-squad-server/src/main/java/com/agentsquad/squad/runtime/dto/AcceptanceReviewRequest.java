package com.agentsquad.squad.runtime.dto;

/**
 * 请求用户确认当前计划的完成条件
 * <p>
 * 固定计划版本和条件身份，防止旧计划确认被新计划采用。
 */
public record AcceptanceReviewRequest(String criterionId, long expectedPlanVersion) {
}
