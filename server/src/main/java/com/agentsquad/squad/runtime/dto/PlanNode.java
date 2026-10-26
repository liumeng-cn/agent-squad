package com.agentsquad.squad.runtime.dto;

import java.util.List;

/**
 * 计划发布中的单个委派节点。
 * <p>
 * dependsOn 引用同一计划内其他节点的 nodeKey；required 标记该节点是否为汇合必需节点。
 */
public record PlanNode(String nodeKey, String workerKey, String workerVersion, String goal,
                       String contextSnapshotId, List<String> dependsOn, boolean required,
                       int maxRecoveryAttempts) {
}
