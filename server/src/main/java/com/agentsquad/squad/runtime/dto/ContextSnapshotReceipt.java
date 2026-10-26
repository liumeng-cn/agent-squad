package com.agentsquad.squad.runtime.dto;

/**
 * 局部材料冻结回执。
 *
 * Master 仅用快照ID引用已授权内容；Worker 实际读取时仍按用户、目标和节点复核。
 */
public record ContextSnapshotReceipt(String snapshotId, String targetNodeKey, String contentHash) {
}
