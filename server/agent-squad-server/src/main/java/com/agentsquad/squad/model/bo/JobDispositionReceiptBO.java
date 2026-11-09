package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.enums.WorkerJobStatus;

/**
 * 工作处置回执
 * <p>
 * 仅确认当前计划内某项工作已收尾，不表示整个用户任务已经完成。
 */
public record JobDispositionReceiptBO(String jobId, long planVersion, WorkerJobStatus status) {
}
