package com.agentsquad.squad.model.bo;

/**
 * 执行记忆快照。
 * <p>
 * 持久冻结的有限记忆集合；Worker只取得显式选择的局部子集。
 */
public record MemoryContextSnapshotBO(long memoryRevision, java.util.List<MemoryReferenceBO> memories) {
}
