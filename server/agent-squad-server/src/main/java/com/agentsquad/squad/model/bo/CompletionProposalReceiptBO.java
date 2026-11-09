package com.agentsquad.squad.model.bo;

/**
 * 完成候选的持久回执；只有原生 Turn 成功结束后才可能变成最终答案。
 */
public record CompletionProposalReceiptBO(String taskId, String bindingId, String turnId,
                                          String toolCallId, String status) {
}
