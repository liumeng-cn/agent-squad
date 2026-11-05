package com.agentsquad.squad.conversation.domain;

/**
 * 回复完成协议
 * <p>
 * 旧在途回复保留候选协议，新回复依据可信自然输出完成，恢复时不随配置切换。
 */
public enum ResponseCompletionProtocol {
    PROPOSAL, NATURAL
}
