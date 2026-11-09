package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 执行活动等待原因
 * <p>
 * 用于持久活动与页面契约，不替代回复、目标或Worker的业务完成状态。
 */
@Getter
public enum ActivityWaitReason {
    CAPACITY(1, "CAPACITY", "容量不足"),
    DEPENDENCY(2, "DEPENDENCY", "依赖等待"),
    INPUT(3, "INPUT", "等待输入"),
    APPROVAL(4, "APPROVAL", "等待审批"),
    EXTERNAL_RESULT(5, "EXTERNAL_RESULT", "等待外部结果"),
    RESUME_READY(6, "RESUME_READY", "可恢复"),
    RETRY_BACKOFF(7, "RETRY_BACKOFF", "重试退避"),
    EVENT_RECOVERY(8, "EVENT_RECOVERY", "事件恢复");

    /**
     * 编码
     */
    private final int code;
    /**
     * 英文名
     */
    private final String name;
    /**
     * 中文描述
     */
    private final String desc;

    ActivityWaitReason(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
