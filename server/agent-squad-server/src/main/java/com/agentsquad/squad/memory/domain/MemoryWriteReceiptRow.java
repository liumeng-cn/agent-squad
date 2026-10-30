package com.agentsquad.squad.memory.domain;

/**
 * 记忆工具幂等事实。
 * <p>
 * 相同执行和逻辑键必须具有相同请求摘要，重放不再次更改记忆。
 */
public record MemoryWriteReceiptRow(String payloadHash, String resultJson) {
}
