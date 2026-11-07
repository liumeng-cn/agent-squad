package com.agentsquad.squad.domain.entity;

/**
 * Worker 局部材料的不可变数据库记录
 *
 * 快照固定目标节点、对话代际和内容摘要；发布计划与投递 Worker 时均须重新核对归属。
 */
public record ContextSnapshotRow(String snapshotId, String userId, String taskId,
                                 String targetNodeKey, String logicalKey, long conversationGeneration,
                                 long memoryRevision, String itemsJson, long tokenEstimate,
                                 String contentHash, long goalRevision) {
}
