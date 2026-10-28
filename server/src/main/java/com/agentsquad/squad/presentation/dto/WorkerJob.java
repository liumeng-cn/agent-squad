package com.agentsquad.squad.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Worker 任务公开视图；结果只在终态成功后呈现，候选和内部输入不公开。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkerJob(String jobId, String taskId, String nodeKey, long planVersion,
                        String workerKey, String workerVersion, String status, long version,
                        String bindingId, String reusedFromJobId, Object result) {
}
