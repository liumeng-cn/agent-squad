package com.agentsquad.squad.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.agentsquad.squad.domain.enums.WorkerJobStatus;
import com.agentsquad.squad.domain.enums.WorkerSkipReason;
import java.util.List;

/**
 * Worker 任务公开视图；结果只在终态成功后呈现，候选和内部输入不公开。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkerJob(String jobId, String taskId, String nodeKey, long planVersion,
                        String workerKey, String workerVersion, WorkerJobStatus status, long version,
                        String bindingId, String reusedFromJobId, Object result, String title,
                        List<String> dependsOn, WorkerSkipReason skipReason, Boolean required) {
}
