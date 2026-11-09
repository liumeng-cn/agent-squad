package com.agentsquad.squad.bo;

/**
 * 任务协调者的修订决定。
 * <p>
 * 只有目标Master可应用或拒绝；应用仍复核当前版本及未完成的外部操作。
 */
public record TaskDirectiveDecisionBO(String directiveId, long expectedVersion, String decision, String reason) {
}
