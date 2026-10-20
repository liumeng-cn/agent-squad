package com.agentsquad.squad.runtime.domain;

/** 当前 Master 能力版本的稳定框架标识；变更能力版本时须提供旧会话恢复策略。 */
public final class MasterAgentIdentity {

    public static final String AGENT_ID = "00000000000000000000000000000001";

    private MasterAgentIdentity() {
    }
}
