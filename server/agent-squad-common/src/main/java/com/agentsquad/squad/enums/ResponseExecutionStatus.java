package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 回复执行生命周期。
 * <p>
 * 普通回复与目标任务独立裁决；连接断开不属于执行状态。
 */
@Getter
public enum ResponseExecutionStatus {
    QUEUED(1, "QUEUED", "排队中"),
    RUNNING(2, "RUNNING", "执行中"),
    WAITING_INPUT(3, "WAITING_INPUT", "等待输入"),
    WAITING_APPROVAL(4, "WAITING_APPROVAL", "等待审批"),
    RECOVERING(5, "RECOVERING", "恢复中"),
    RECONCILING(6, "RECONCILING", "核对中"),
    CANCELLING(7, "CANCELLING", "取消中"),
    SUCCEEDED(8, "SUCCEEDED", "已成功"),
    PARTIAL(9, "PARTIAL", "部分成功"),
    FAILED(10, "FAILED", "已失败"),
    CANCELLED(11, "CANCELLED", "已取消");

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

    ResponseExecutionStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
