package com.agentsquad.squad.model.bo;

import io.agentscope.harness.agent.HarnessAgent;

/**
 * 本进程对固定 Worker Binding 的原生 Agent 与业务 guard 持有记录。
 */
public record WorkerAgentHandleBO(HarnessAgent agent, Long userId, Long bindingId,
                                  String sessionId, String agentId, String guardToken) {
}
