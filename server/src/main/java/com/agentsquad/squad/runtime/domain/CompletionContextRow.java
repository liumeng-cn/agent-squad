package com.agentsquad.squad.runtime.domain;

/**
 * Master 完成候选提交上下文。
 * <p>
 * 由受控会话绑定和目标当前版本构成，模型不能自行指定归属、计划版本或答案版本。
 */
public record CompletionContextRow(String userId, String taskId, String bindingId,
                                   String taskStatus, long planVersion, long answerRevision,
                                   String proposalTurnId, String proposalHash,
                                   String proposalToolCallId, String proposalJson,
                                   boolean cancellationRequested) {
}
