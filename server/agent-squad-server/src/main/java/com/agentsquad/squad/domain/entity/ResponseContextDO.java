package com.agentsquad.squad.domain.entity;

/**
 * 回复上下文冻结点。
 * <p>
 * 从固定绑定解析来源消息和已有快照，不接受模型自报会话或消息范围。
 */
public record ResponseContextDO(String executionId, String sourceMessageId, long sourceSequence,
                                 String contextJson, String contextHash) {
}
