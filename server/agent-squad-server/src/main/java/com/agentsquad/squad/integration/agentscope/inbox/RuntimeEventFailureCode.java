package com.agentsquad.squad.integration.agentscope.inbox;

/**
 * 原生消费失败的安全分类
 * <p>
 * 短暂缺失或业务失败允许重试；事实冲突及未知必需事件需要核对，不保存原始异常正文。
 */
public enum RuntimeEventFailureCode {
    RECEIPT_MISSING(RuntimeEventInboxStatus.RETRY),
    EVENT_GAP(RuntimeEventInboxStatus.RETRY),
    BUSINESS_RETRY(RuntimeEventInboxStatus.RETRY),
    RECEIPT_MISMATCH(RuntimeEventInboxStatus.BLOCKED),
    UNSUPPORTED_REQUIRED(RuntimeEventInboxStatus.BLOCKED);

    private final RuntimeEventInboxStatus status;

    RuntimeEventFailureCode(RuntimeEventInboxStatus status) {
        this.status = status;
    }

    public RuntimeEventInboxStatus status() {
        return status;
    }
}
