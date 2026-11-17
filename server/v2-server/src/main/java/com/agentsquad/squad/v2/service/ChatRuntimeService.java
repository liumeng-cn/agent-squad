package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.converter.ChatSessionConverter;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.session.AgentSession;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 原生聊天会话访问
 * <p>
 * 使用服务端用户身份和固定 Agent 定位原生会话；读取不启动执行，不领取执行租约。
 */
@Service
public class ChatRuntimeService {
    @Resource
    private ObjectProvider<HarnessAgent> agentProvider;

    @Resource
    private ChatSessionConverter converter;

    @Resource
    private ChatSessionService previews;

    /**
     * 打开有身份隔离的原生句柄，不创建第二套执行注册表。
     */
    public AgentSession open(String sessionId, String userId) {
        var agent = agentProvider.getIfAvailable();
        if (agent == null) {
            throw new IllegalStateException("聊天模型尚未启用");
        }
        return agent.session(RuntimeContext.builder().userId(userId).sessionId(sessionId).build());
    }

    public boolean isPreview(String sessionId) {
        return java.util.Set.of("demo-welcome", "demo-api", "demo-plan").contains(sessionId);
    }

    /**
     * 在已提交前缀上构造公开会话，不恢复执行、不调用模型和工具。
     */
    public ChatSessionVO read(String sessionId, String userId) {
        if (isPreview(sessionId)) {
            return previews.read(sessionId, userId);
        }
        return converter.snapshot(open(sessionId, userId), sessionId);
    }
}
