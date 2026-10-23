package com.agentsquad.squad.tools.operation;

import java.util.Map;

/**
 * 外部操作预检请求。
 * <p>
 * 工具包装层从可信运行上下文构建执行身份，模型参数只能进入 arguments。
 */
public record PrepareRequest(String userId, String sessionId, String frameworkTurnId,
                             String toolCallId, String toolKey, String toolVersion,
                             String effect, Map<String, Object> arguments,
                             String businessIntentKey, boolean requiresApproval,
                             String connectionId, long connectionRevision,
                             long reservedUnits, String creatorName) {
}
