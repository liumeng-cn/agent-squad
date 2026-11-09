package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 委派工作状态
 * <p>
 * 区分依赖等待、实际执行、等待恢复及明确结束；等待恢复不能当作依赖已经彻底失败。
 */
@Getter
public enum WorkerJobStatus {
    BLOCKED(1, "BLOCKED", "依赖阻塞"),
    READY(2, "READY", "就绪"),
    RUNNING(3, "RUNNING", "执行中"),
    WAITING_INPUT(4, "WAITING_INPUT", "等待输入"),
    WAITING_APPROVAL(5, "WAITING_APPROVAL", "等待审批"),
    RECOVERING(6, "RECOVERING", "恢复中"),
    RECOVERY_BLOCKED(7, "RECOVERY_BLOCKED", "恢复阻塞"),
    RECONCILING(8, "RECONCILING", "核对中"),
    CANCELLING(9, "CANCELLING", "取消中"),
    SUCCEEDED(10, "SUCCEEDED", "已成功"),
    PARTIAL(11, "PARTIAL", "部分成功"),
    FAILED(12, "FAILED", "已失败"),
    CANCELLED(13, "CANCELLED", "已取消"),
    SKIPPED(14, "SKIPPED", "已跳过"),
    SUPERSEDED(15, "SUPERSEDED", "已替换");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    WorkerJobStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public boolean preventsDependentExecution() {
        return this == FAILED || this == PARTIAL || this == CANCELLED || this == SKIPPED;
    }
}
