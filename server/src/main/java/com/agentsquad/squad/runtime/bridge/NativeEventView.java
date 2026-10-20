package com.agentsquad.squad.runtime.bridge;

/**
 * 投影器可读取的最小原生事件信息；不携带模型原始消息或工具载荷。
 */
public record NativeEventView(long sequence, String eventId, String type,
                              String runId, String turnId, String outcome, long occurredAtMillis) {
}
