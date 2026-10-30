package com.agentsquad.squad.memory.domain;

/**
 * 显式记忆授权来源。
 * <p>
 * 通过当前回复绑定定位持久用户消息，工具不能指定其他消息。
 */
public record MemorySource(String messageId, String content) {
}
