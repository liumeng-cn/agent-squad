package com.agentsquad.squad.model.bo;

/**
 * 放弃恢复受限工作的决定
 * <p>
 * 计划版本必须匹配当前目标；用户及协调会话身份由运行上下文提供。
 */
public record AbandonFailedJobRequestBO(String jobId, long expectedPlanVersion) {
}
