package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.converter.ChatConvConverter;
import com.agentsquad.squad.v2.converter.ChatSessionConverter;
import com.agentsquad.squad.v2.po.ChatConvPO;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.session.AgentSession;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;


/**
 * 原生聊天会话访问
 * <p>
 * 使用服务端用户身份和固定 Agent 定位原生会话；读取不启动执行，不领取执行租约。
 */
@Service
public class ChatRuntimeService {
    @Resource
    private HarnessAgent agent;

    @Resource
    private ChatSessionConverter converter;

    @Resource
    private ChatConvService convs;

    @Resource
    private ChatConvConverter conversationConverter;

    /**
     * 校验业务归属后打开有身份隔离的原生句柄
     */
    public AgentSession open(String sessionId, String userId) {
        var conv = convs.requireSession(sessionId, userId);
        return openNative(conv);
    }

    /**
     * 核对对话归属供执行入口和 SSE 使用
     */
    public void verifyAccess(String sessionId, String userId) {
        convs.requireSession(sessionId, userId);
    }

    /**
     * 读取原生历史并填充业务目录摘要
     */
    public ChatSessionVO read(String sessionId, String userId) {
        var conv = convs.requireSession(sessionId, userId);
        var session = openNative(conv);
        var snapshot = converter.snapshot(session, sessionId);
        snapshot.setSummary(conversationConverter.toSummary(conv));
        return snapshot;
    }

    private AgentSession openNative(ChatConvPO conv) {
        if (!conv.getAgentId().equals(agent.getAgentId())) {
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "对话关联的 Agent 不可用");
        }
        var context = RuntimeContext.builder()
                .userId(conv.getUserId().toString())
                .sessionId(conv.getSessionId())
                .build();
        return agent.session(context);
    }

}
