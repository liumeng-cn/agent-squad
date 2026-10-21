package com.agentsquad.squad.runtime.dto;

import java.util.List;

/**
 * 任务详情页快照；聚合目标状态、原始目标文本、Worker 任务、待处理交互及当前答案游标。
 */
public record TaskSnapshot(TaskResponse task, String goalMessage, List<WorkerJob> jobs, List<Interaction> pending,
                           AnswerView answer, String cursor) {
}
