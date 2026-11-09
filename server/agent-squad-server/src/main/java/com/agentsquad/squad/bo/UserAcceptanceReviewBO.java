package com.agentsquad.squad.bo;

/**
 * 平台生成的用户验收问题
 * <p>
 * 问题、摘要与计划版本由持久计划生成，模型不能自行改写用户确认对应的内容。
 */
public record UserAcceptanceReviewBO(String criterionId, long planVersion, String question, String requestHash) {
}
