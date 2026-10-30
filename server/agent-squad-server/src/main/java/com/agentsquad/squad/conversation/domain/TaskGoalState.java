package com.agentsquad.squad.conversation.domain;

/**
 * 目标语义修订事实。
 * <p>
 * 与运行版本分开读取，防止单纯的等待和恢复变化使用户修改失效。
 */
public record TaskGoalState(long goalRevision, String inputJson) {
}
