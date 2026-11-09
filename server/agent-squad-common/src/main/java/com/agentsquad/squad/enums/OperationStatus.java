package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 外部操作生命周期；UNKNOWN 只能经核对出账，禁止跳到成功终态。
 */
@Getter
public enum OperationStatus {
    PREPARED(1, "PREPARED", "已准备"),
    WAITING_APPROVAL(2, "WAITING_APPROVAL", "等待审批"),
    IN_FLIGHT(3, "IN_FLIGHT", "执行中"),
    UNKNOWN(4, "UNKNOWN", "未知"),
    RECONCILING(5, "RECONCILING", "核对中"),
    SUCCEEDED(6, "SUCCEEDED", "已成功"),
    FAILED(7, "FAILED", "已失败"),
    CANCELLED(8, "CANCELLED", "已取消");

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

    OperationStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 是否允许新的审批或派发动作推进该状态。
     */
    public boolean isAdvanceable() {
        return this == PREPARED || this == WAITING_APPROVAL;
    }
}
