package com.agentsquad.squad.conversation.domain;

/**
 * 执行所有者的当前状态。
 * <p>
 * 用于共享派发屏障；回复与目标独立存储，仅统一读取状态、取消意图与逻辑删除。
 */
public record ExecutionOwnerRow(String status, boolean cancellationRequested, String deleted) {
}
