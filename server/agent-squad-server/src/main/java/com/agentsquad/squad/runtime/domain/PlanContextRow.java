package com.agentsquad.squad.runtime.domain;

/**
 * publish_plan 的锁定上下文。
 * <p>
 * 由受控 Master 会话和目标当前版本构成；模型不能自行指定归属、计划版本或答案版本。
 */
public record PlanContextRow(String userId, String taskId, String bindingId, long planVersion,
                             String taskStatus, boolean cancellationRequested,
                             long conversationGeneration, String conversationStatus) {
}
