package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.converter.ChatConvConverter;
import com.agentsquad.squad.v2.converter.ChatSessionConverter;
import com.agentsquad.squad.v2.po.ChatConvPO;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.session.AgentSession;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 原生聊天会话访问
 * <p>
 * 以业务对话为入口：先按对话标识核对当前用户归属，再打开身份隔离的原生句柄；
 * 读取不启动执行，不领取执行租约。
 */
@Service
public class ChatRuntimeService {
    @Resource
    private ChatSessionConverter converter;

    @Resource
    private ChatConvService convs;

    @Resource
    private ChatConvConverter convConverter;

    /**
     * 校验业务归属后打开原生句柄
     */
    public AgentSession open(String convId) {
        return openNative(convs.require(convId));
    }

    /**
     * 核对对话归属供执行入口和 SSE 使用
     */
    public void verifyAccess(String convId) {
        convs.require(convId);
    }

    /**
     * 读取原生历史并填充业务目录摘要
     */
    public ChatSessionVO read(String convId) {
        ChatConvPO conv = convs.require(convId);
        return snapshot(conv);
    }

    /**
     * 在请求线程确认归属，供后台订阅固定身份使用。
     */
    ChatConvPO authorizeSubscription(String convId) {
        return convs.require(convId);
    }

    AgentSession openAuthorized(ChatConvPO conv) {
        return openNative(conv);
    }

    ChatSessionVO snapshot(ChatConvPO conv, AgentSession session) {
        ChatSessionVO snapshot = converter.snapshot(session, conv.getSessionId());
        snapshot.setSummary(convConverter.toSummary(conv));
        return snapshot;
    }

    private ChatSessionVO snapshot(ChatConvPO conv) {
        return snapshot(conv, openNative(conv));
    }

    private AgentSession openNative(ChatConvPO conv) {
        var context = RuntimeContext.builder()
                .userId(conv.getUserId().toString())
                .sessionId(conv.getSessionId())
                .build();
        return convs.resolveAgent(conv).session(context);
    }
}
