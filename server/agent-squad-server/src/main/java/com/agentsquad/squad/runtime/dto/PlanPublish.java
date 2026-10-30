package com.agentsquad.squad.runtime.dto;

import java.util.List;

/**
 * Master 工具 publish_plan 的输入。
 * <p>
 * nodes 为不可变 DAG；joinPolicy 决定汇合标准，acceptanceCriteria 是完成条件。
 */
public record PlanPublish(String logicalKey, long expectedPlanVersion, List<PlanNode> nodes,
                          String joinPolicy, List<String> acceptanceCriteria) {
}
