package com.agentsquad.squad.domain.model;

import com.agentsquad.squad.domain.enums.GoalTaskStatus;
import com.agentsquad.squad.domain.enums.PlanJoinStage;

/**
 * 计划汇总与完成判断
 * <p>
 * 只有 READY 提供任务结论；该结论仍须由应用服务核对目标版本和发布权限后保存。
 */
public record PlanJoinDecision(PlanJoinStage stage, GoalTaskStatus outcome) {
}
