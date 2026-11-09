package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 原生消费失败的安全分类
 * <p>
 * 短暂缺失或业务失败允许重试；事实冲突及未知必需事件需要核对，不保存原始异常正文。
 */
@Getter
public enum RuntimeEventFailureCode {
    RECEIPT_MISSING(RuntimeEventInboxStatus.RETRY, 1, "RECEIPT_MISSING", "回执缺失"),
    EVENT_GAP(RuntimeEventInboxStatus.RETRY, 2, "EVENT_GAP", "事件缺口"),
    BUSINESS_RETRY(RuntimeEventInboxStatus.RETRY, 3, "BUSINESS_RETRY", "业务可重试"),
    RECEIPT_MISMATCH(RuntimeEventInboxStatus.BLOCKED, 4, "RECEIPT_MISMATCH", "回执不一致"),
    UNSUPPORTED_REQUIRED(RuntimeEventInboxStatus.BLOCKED, 5, "UNSUPPORTED_REQUIRED", "必需事件不支持");

    /** 收件处理状态 */
    private final RuntimeEventInboxStatus status;
    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    RuntimeEventFailureCode(RuntimeEventInboxStatus status, int code, String name, String desc) {
        this.status = status;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public RuntimeEventInboxStatus status() {
        return status;
    }
}
