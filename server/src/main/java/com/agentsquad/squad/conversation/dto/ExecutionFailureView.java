package com.agentsquad.squad.conversation.dto;

import java.util.List;

/**
 * 面向用户的执行故障说明。
 * <p>
 * 仅公开安全原因、处理责任和允许动作；不承载堆栈、凭据或原生模型上下文。
 */
public record ExecutionFailureView(String failureId, String scopeId, String category, String code,
                                   String actor, String safeMessage, boolean retryable, int attempt,
                                   int maxAttempts, String diagnosticId, List<String> allowedActions) {
}
