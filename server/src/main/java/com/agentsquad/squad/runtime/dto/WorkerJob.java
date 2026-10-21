package com.agentsquad.squad.runtime.dto;

/**
 * Worker 任务公开视图；D1 直接回答阶段无 Worker 任务。
 */
public record WorkerJob(String jobId, String taskId, String nodeKey, long planVersion,
                        String workerKey, String workerVersion, String status, long version,
                        String bindingId, Object result) {
}
