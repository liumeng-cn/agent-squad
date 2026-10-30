package com.agentsquad.squad.integration.agentscope;

import java.util.Map;

/**
 * 投影器可读取的最小原生事件信息；data 只含交互绑定所需的固定字段，不携带模型原始消息。
 */
public record NativeEventView(long sequence, String eventId, String type,
                              String runId, String turnId, String outcome, long occurredAtMillis,
                              Map<String, Object> data) {
}
