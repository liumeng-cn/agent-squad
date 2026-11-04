package com.agentsquad.squad.integration.agentscope.inbox;

/**
 * 原生事件收件处理状态
 * <p>
 * 只表示平台消费进度，不替代SDK运行状态或回复、任务的业务结果。
 */
public enum RuntimeEventInboxStatus {
    RECEIVED, APPLIED, RETRY, BLOCKED
}
