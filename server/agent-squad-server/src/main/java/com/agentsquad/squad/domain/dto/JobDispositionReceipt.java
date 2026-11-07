package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.common.enums.worker.WorkerJobStatus;

/**
 * 工作处置回执
 * <p>
 * 仅确认当前计划内某项工作已收尾，不表示整个用户任务已经完成。
 */
public record JobDispositionReceipt(String jobId, long planVersion, WorkerJobStatus status) {
}
