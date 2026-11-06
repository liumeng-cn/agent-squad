package com.agentsquad.squad.common.enums.worker;

/**
 * 委派工作未执行的原因
 * <p>
 * 当前SKIPPED仅表示硬依赖已无法成功，取消和计划替换仍使用各自状态。
 */
public enum WorkerSkipReason {
    DEPENDENCY_FAILED
}
