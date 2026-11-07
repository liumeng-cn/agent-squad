package com.agentsquad.squad.domain.dto;

import java.util.List;

/**
 * 当前协调回合提交的最终回答与检查意见
 * <p>
 * 用户和会话来自受控运行上下文；逐项意见仍需服务端验证，不能直接决定目标成功。
 */
public record CompletionSubmission(String userId, String sessionId, String turnId, String toolCallId,
                                   String outcome, String finalAnswer, List<AcceptanceReview> reviews) {
}
