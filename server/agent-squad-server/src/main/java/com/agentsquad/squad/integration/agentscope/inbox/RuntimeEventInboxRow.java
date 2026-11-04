package com.agentsquad.squad.integration.agentscope.inbox;

/**
 * 原生事件安全定位记录
 * <p>
 * 收件只保留不可变信封及摘要；正文、思考、工具参数和请求上下文继续由官方日志保存。
 */
public record RuntimeEventInboxRow(String inboxId, String userId, String bindingId, String sessionId,
                                   long nativeSeq, String nativeEventId, String nativeType,
                                   String runId, String turnId, long occurredAtMillis, boolean required,
                                   int nativeSchemaVersion, String payloadDigest, int mappingVersion,
                                   RuntimeEventInboxStatus status) {
}
