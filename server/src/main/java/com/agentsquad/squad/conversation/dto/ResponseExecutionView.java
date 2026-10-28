package com.agentsquad.squad.conversation.dto;

import com.agentsquad.squad.conversation.domain.ResponseExecutionStatus;

import java.util.List;

/**
 * 用户可观察的回复执行视图。
 * <p>
 * 状态与允许动作来自服务端事实，停止回答不会隐式终结关联目标。
 */
public record ResponseExecutionView(String executionId, String sourceMessageId, ResponseExecutionStatus status,
                                    long version, String answerMessageId, String failureId,
                                    List<String> allowedActions) {
}
