package com.agentsquad.squad.runtime.domain;

/**
 * 计划结果汇总阶段
 * <p>
 * 执行等待、恢复协调、效果核对与最终结论分开，不能把协调机会当作发布许可。
 */
public enum PlanJoinStage {
    WAITING_WORK,
    NEEDS_COORDINATION,
    WAITING_EFFECTS,
    READY
}
