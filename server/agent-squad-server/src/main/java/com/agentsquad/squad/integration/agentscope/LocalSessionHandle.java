package com.agentsquad.squad.integration.agentscope;

import io.agentscope.harness.agent.session.AgentSession;

/**
 * 本进程实际启动过的原生会话
 * <p>
 * 保留用户与绑定归属以观察持久停止意图，不保存到数据库，不跨节点传递 SDK 对象。
 */
public record LocalSessionHandle(String userId, String bindingId, AgentSession session) {
    public String key() {
        return userId + ":" + bindingId;
    }
}
