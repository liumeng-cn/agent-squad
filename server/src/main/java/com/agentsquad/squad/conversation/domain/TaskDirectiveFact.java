package com.agentsquad.squad.conversation.domain;

/**
 * 会话回复可读取的修订状态。
 * <p>
 * 保留来源消息与实际应用版本，不将受理状态表述成已应用。
 */
public record TaskDirectiveFact(String directiveId, String sourceMessageId, String kind, String status,
                                Long appliedVersion, Long goalRevision, String reasonCode, String reasonMessage) {
}
