package com.agentsquad.squad.runtime.d1;

/** D1 用户目标的最小外部投影。 */
public record D1Task(String id, String conversationId, String userId, String message, String commandId, String status) {
}
