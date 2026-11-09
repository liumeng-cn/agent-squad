package com.agentsquad.squad.bo;

import com.agentsquad.squad.vo.MessageVO;
import com.agentsquad.squad.vo.ResponseExecutionVO;

/**
 * 用户消息受理增量
 * <p>
 * 携带已保存消息和回复状态，页面无需再次读取完整会话才能显示新消息。
 */
public record MessageAcceptedPayloadBO(int schemaVersion, MessageVO message, ResponseExecutionVO execution) {
}
