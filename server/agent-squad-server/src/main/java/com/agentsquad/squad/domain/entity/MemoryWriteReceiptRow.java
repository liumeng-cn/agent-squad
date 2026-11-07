package com.agentsquad.squad.domain.entity;

/**
 * 记忆工具调用记录，用于识别重复请求。
 * <p>
 * 相同执行和逻辑键必须具有相同请求摘要，重放不再次更改记忆。
 */
public record MemoryWriteReceiptRow(String payloadHash, String resultJson) {
}
