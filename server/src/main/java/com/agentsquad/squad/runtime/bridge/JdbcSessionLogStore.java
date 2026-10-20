package com.agentsquad.squad.runtime.bridge;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.JournalSessionLog;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLog;
import io.agentscope.core.session.SessionLogStore;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** 将固定的用户、Agent 和会话标识映射到 AgentScope 原生 Journal。 */
@Component
public class JdbcSessionLogStore implements SessionLogStore {

    @Resource
    private JdbcNativeSessionStorage storage;

    /** 按框架定义的路径打开日志；调用方仍需从受权 Binding 获取 SessionKey。 */
    @Override
    public SessionLog open(SessionKey key, RuntimeContext context) {
        return new JournalSessionLog(storage, key.storagePath());
    }
}
