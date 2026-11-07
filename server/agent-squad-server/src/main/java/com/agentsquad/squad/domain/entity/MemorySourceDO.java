package com.agentsquad.squad.domain.entity;

/**
 * 显式记忆授权来源。
 * <p>
 * 通过当前回复绑定定位持久用户消息，工具不能指定其他消息。
 */
public record MemorySourceDO(String messageId, String content) {
}
