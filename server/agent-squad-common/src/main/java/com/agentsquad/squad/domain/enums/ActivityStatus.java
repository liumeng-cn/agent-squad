package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 执行活动状态
 * <p>
 * 用于持久活动与页面契约，不替代回复、目标或Worker的业务完成状态。
 */
@Getter
public enum ActivityStatus {
    QUEUED(1, "QUEUED", "排队中"),
    RUNNING(2, "RUNNING", "执行中"),
    WAITING(3, "WAITING", "等待中"),
    SUCCEEDED(4, "SUCCEEDED", "已成功"),
    FAILED(5, "FAILED", "已失败"),
    CANCELLED(6, "CANCELLED", "已取消"),
    UNKNOWN(7, "UNKNOWN", "未知");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ActivityStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
