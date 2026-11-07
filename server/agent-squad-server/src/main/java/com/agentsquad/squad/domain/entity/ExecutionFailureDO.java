package com.agentsquad.squad.domain.entity;

/**
 * 执行故障的数据库记录
 * <p>
 * 只保存稳定分类与安全说明，不存储异常原文、密钥或模型上下文。
 */
public record ExecutionFailureDO(String failureId, String scopeId, String bindingId, String category, String code,
                                  String actor,
                                  String safeMessage, String diagnosticId, String status, int retryAttempt) {
}
