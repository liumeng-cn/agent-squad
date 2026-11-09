package com.agentsquad.squad.model.bo;

/**
 * 目标修订的持久状态收据。
 * <p>
 * ACCEPTED不表示已生效；应用后才携带目标版本和语义修订号。
 */
public record TaskDirectiveReceiptBO(Long directiveId, Long taskId, Long sourceMessageId, String status,
                                     Long appliedVersion, Long goalRevision, String reasonCode, String reasonMessage) {
}
