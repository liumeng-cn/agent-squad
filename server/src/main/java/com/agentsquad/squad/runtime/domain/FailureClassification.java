package com.agentsquad.squad.runtime.domain;

/**
 * 结构化故障的分类决定。
 * <p>
 * 分类只依据异常类型和协议状态，不使用异常文本猜测责任或权限。
 */
public record FailureClassification(String category, String code, String actor, String safeMessage) {
}
