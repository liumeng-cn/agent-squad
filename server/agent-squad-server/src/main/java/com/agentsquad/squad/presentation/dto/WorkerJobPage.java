package com.agentsquad.squad.presentation.dto;

import java.util.List;

/**
 * 当前计划的子任务集合。
 * <p>
 * 节点数受计划上限约束，接口保留标准分页结构以供工作台统一消费。
 */
public record WorkerJobPage(List<WorkerJob> items, String nextCursor) {
}
