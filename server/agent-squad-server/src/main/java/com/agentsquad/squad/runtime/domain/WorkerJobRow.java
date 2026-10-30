package com.agentsquad.squad.runtime.domain;

import java.time.LocalDateTime;

/**
 * 委派节点的持久投影。
 * <p>
 * 一节点一行；status 由平台状态机推进，input_json 为固定局部输入，result_json 为最终结构化结果，
 * cancel_requested_at 记录新计划替代时的取消屏障。
 */
public record WorkerJobRow(long id, String jobId, String userId, String taskId, long planVersion,
                           String nodeKey, String workerKey, String workerVersion, String status,
                           String dependsOnJson, String inputJson, String resultCandidateJson,
                           String resultJson, String bindingId, String reusedFromJobId,
                           LocalDateTime cancelRequestedAt, long version) {
}
