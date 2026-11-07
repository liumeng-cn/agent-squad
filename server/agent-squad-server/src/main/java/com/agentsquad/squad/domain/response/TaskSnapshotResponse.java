package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.AnswerView;
import com.agentsquad.squad.domain.dto.TaskResponse;
import com.agentsquad.squad.domain.dto.WorkerJob;

import java.util.List;

/**
 * 任务详情页快照；聚合目标状态、原始目标文本、Worker 任务、待处理交互及当前答案游标。
 */
public record TaskSnapshotResponse(TaskResponse task, String goalMessage, List<WorkerJob> jobs, List<InteractionResponse> pending,
                           AnswerView answer, String cursor) {
}
