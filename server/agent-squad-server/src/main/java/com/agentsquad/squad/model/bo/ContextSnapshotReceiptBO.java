package com.agentsquad.squad.model.bo;

/**
 * 局部材料冻结回执。
 * <p>
 * Master 仅用快照ID引用已授权内容；Worker 实际读取时仍按用户、目标和节点复核。
 */
public record ContextSnapshotReceiptBO(String snapshotId, String targetNodeKey, String contentHash) {
}
