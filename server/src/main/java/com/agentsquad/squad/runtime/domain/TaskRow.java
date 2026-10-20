package com.agentsquad.squad.runtime.domain;

/**
 * 用户目标持久层投影；原生 Turn 和 Run 不属于此对象。
 */
public record TaskRow(String taskId, String userId, String conversationId, String status, long version) {
}
