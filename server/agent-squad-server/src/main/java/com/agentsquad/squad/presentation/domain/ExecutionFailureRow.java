package com.agentsquad.squad.presentation.domain;

/**
 * 执行故障的持久投影。
 * <p>
 * 只保存稳定分类与安全说明，不存储异常原文、密钥或模型上下文。
 */
public record ExecutionFailureRow(String failureId, String scopeId, String bindingId, String category, String code,
                                  String actor,
                                  String safeMessage, String diagnosticId, String status, int retryAttempt) {
}
