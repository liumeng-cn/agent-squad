package com.agentsquad.squad.conversation.dto;

/**
 * 目标修订的持久状态收据。
 * <p>
 * ACCEPTED不表示已生效；应用后才携带目标版本和语义修订号。
 */
public record TaskDirectiveReceipt(String directiveId, String taskId, String sourceMessageId, String status,
                                   Long appliedVersion, Long goalRevision, String reasonCode, String reasonMessage) {
}
