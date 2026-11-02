package com.agentsquad.squad.conversation.domain;

/**
 * 原生运行容量占位。
 *
 * 一条 Binding 同时最多持有一个活动占位；命令与原生 Turn 是持久归属，
 * 恢复命令须有后续新原生 Run 身份，不能凭旧 Turn 判定回收。
 */
public record RuntimeAdmissionSlot(String userId, String bindingId, String commandId,
                                   String action, String turnId, String runId,
                                   long claimCursor, String status) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
