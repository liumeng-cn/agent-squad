package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.enums.WorkerJobStatus;

/**
 * 当前计划参与汇总的工作
 * <p>
 * 保留计划版本、必要性和可信执行状态，避免把旧计划结果混入当前完成判断。
 */
public record PlanJoinJobBO(String jobId, long planVersion, boolean required, WorkerJobStatus status) {
}
