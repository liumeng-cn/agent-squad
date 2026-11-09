package com.agentsquad.squad.model.bo;

/**
 * 结构化故障的分类决定。
 * <p>
 * 分类只依据异常类型和协议状态，不使用异常文本猜测责任或权限。
 */
public record FailureClassificationBO(String category, String code, String actor, String safeMessage) {
}
