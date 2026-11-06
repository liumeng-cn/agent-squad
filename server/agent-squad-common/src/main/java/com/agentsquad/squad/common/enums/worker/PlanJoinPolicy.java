package com.agentsquad.squad.common.enums.worker;

/**
 * 计划结果汇总策略
 * <p>
 * 两种策略均等待全部工作收尾；尽力交付不代表可以省略必要工作或验收。
 */
public enum PlanJoinPolicy {
    ALL_REQUIRED,
    BEST_EFFORT
}
