package com.agentsquad.squad.conversation.domain;

/**
 * 持久命令领取时的服务端归属。
 * <p>
 * 用于先锁会话和执行所有者，再领取命令；不从用户请求接受Scope或绑定身份。
 */
public record CommandClaimContext(String userId, String scopeId, String action, String bindingId) {
}
