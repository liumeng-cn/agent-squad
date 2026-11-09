package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 用户目标的产品状态。
 * <p>
 * 数据库存储枚举名称；运行控制只依据平台状态裁决，不能直接以原生回合状态替代。
 */
@Getter
public enum GoalTaskStatus {
    QUEUED(1, "QUEUED", "排队中"),
    RUNNING(2, "RUNNING", "执行中"),
    WAITING_WORKERS(3, "WAITING_WORKERS", "等待子工作"),
    WAITING_INPUT(4, "WAITING_INPUT", "等待输入"),
    WAITING_APPROVAL(5, "WAITING_APPROVAL", "等待审批"),
    RECOVERY_BLOCKED(6, "RECOVERY_BLOCKED", "恢复阻塞"),
    RECONCILING(7, "RECONCILING", "核对中"),
    CANCELLING(8, "CANCELLING", "取消中"),
    SUCCEEDED(9, "SUCCEEDED", "已成功"),
    PARTIAL(10, "PARTIAL", "部分成功"),
    FAILED(11, "FAILED", "已失败"),
    CANCELLED(12, "CANCELLED", "已取消");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    GoalTaskStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public boolean isTerminal() {
        return switch (this) {
            case SUCCEEDED, PARTIAL, FAILED, CANCELLED -> true;
            default -> false;
        };
    }

    public boolean isResumable() {
        return switch (this) {
            case WAITING_INPUT, WAITING_APPROVAL, RECOVERY_BLOCKED, RECONCILING -> true;
            default -> false;
        };
    }
}
