package com.agentsquad.squad.conversation.dto;

/**
 * 任务协调者的修订决定。
 * <p>
 * 只有目标Master可应用或拒绝；应用仍复核当前版本及未完成的外部操作。
 */
public record TaskDirectiveDecision(String directiveId, long expectedVersion, String decision, String reason) {
}
