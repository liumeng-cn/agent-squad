package com.agentsquad.squad.conversation.domain;

/**
 * 原生运行容量占位。
 *
 * 一条 Binding 同时最多持有一个活动占位；命令与原生 Turn 是持久归属，
 * 不能根据命令投递租约过期推断原生运行已经结束。
 */
public record RuntimeAdmissionSlot(String userId, String bindingId, String commandId,
                                   String action, String turnId, String status) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
