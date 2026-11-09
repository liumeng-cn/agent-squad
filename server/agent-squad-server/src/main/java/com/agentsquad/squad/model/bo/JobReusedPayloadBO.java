package com.agentsquad.squad.model.bo;

/**
 * 当前计划采用已保存结果的说明
 * <p>
 * 保留新工作与来源工作的关系，不将复用描述成重新执行。
 */
public record JobReusedPayloadBO(int schemaVersion, String taskId, long planVersion, String jobId,
                                 String reusedFromJobId) {
}
