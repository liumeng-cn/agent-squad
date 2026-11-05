package com.agentsquad.squad.conversation.domain;

/**
 * 自然回复的已提交输出证据
 * <p>
 * 仅由官方日志适配生成；正文与完成程度分开，不将模型运行结束直接等同于业务成功。
 */
public record NaturalReplyEvidence(ResponseExecutionStatus status, String text, String notice) {
}
