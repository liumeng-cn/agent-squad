package com.agentsquad.squad.runtime.domain;

/**
 * 局部上下文准备时的任务事实。
 *
 * 由受控 Master 会话查得用户原始目标与对话代际，不接受模型声明的用户身份或材料来源。
 */
public record ContextPreparationRow(String userId, String taskId, String inputJson,
                                    String taskStatus, boolean cancellationRequested,
                                    long conversationGeneration, String conversationStatus) {
}
