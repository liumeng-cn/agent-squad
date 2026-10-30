package com.agentsquad.squad.conversation.dto;

import com.agentsquad.squad.conversation.domain.ResponseExecutionStatus;

/**
 * 消息持久受理收据。
 * <p>
 * 202 只证明持久接受；不证明模型启动，也不意味着已经创建目标任务。
 */
public record MessageReceipt(String conversationId, String messageId, String executionId,
                             String commandId, ResponseExecutionStatus status, String cursor) {
}
