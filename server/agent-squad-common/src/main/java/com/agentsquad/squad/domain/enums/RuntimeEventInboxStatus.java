package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 原生事件收件处理状态
 * <p>
 * 只表示平台消费进度，不替代SDK运行状态或回复、任务的业务结果。
 */
@Getter
public enum RuntimeEventInboxStatus {
    RECEIVED(1, "RECEIVED", "已接收"),
    APPLIED(2, "APPLIED", "已应用"),
    RETRY(3, "RETRY", "待重试"),
    BLOCKED(4, "BLOCKED", "已阻塞");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    RuntimeEventInboxStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
