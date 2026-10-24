package com.agentsquad.squad.presentation.dto;

/**
 * 完成候选的持久回执；只有原生 Turn 成功结束后才可能变成最终答案。
 */
public record CompletionProposalReceipt(String taskId, String bindingId, String turnId,
                                        String toolCallId, String status) {
}
