package com.agentsquad.squad.runtime.domain;

/** Master 会话与目标的受控关联，以及已持久化的完成候选摘要。 */
public record CompletionContextRow(String userId, String taskId, String bindingId,
                                   String taskStatus, String proposalTurnId, String proposalHash,
                                   String proposalToolCallId, String proposalJson,
                                   boolean cancellationRequested) {
}
