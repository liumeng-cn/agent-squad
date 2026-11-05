package com.agentsquad.squad.presentation.dto;

import com.agentsquad.squad.conversation.dto.MessageView;
import com.agentsquad.squad.conversation.dto.ResponseExecutionView;

/**
 * 用户消息受理增量
 * <p>
 * 携带已保存消息和回复状态，页面无需再次读取完整会话才能显示新消息。
 */
public record MessageAcceptedPayload(int schemaVersion, MessageView message, ResponseExecutionView execution) {
}
