package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 已提交原生收据的适配状态
 * <p>
 * 只映射SDK运行事实，UNKNOWN不推断为成功；不替代回复、任务或Job的业务状态。
 */
@Getter
public enum NativeTaskState {
    QUEUED("queued", 1, "QUEUED", "已排队"),
    RUNNING("running", 2, "RUNNING", "执行中"),
    COMPLETED("completed", 3, "COMPLETED", "已完成"),
    SUSPENDED("suspended", 4, "SUSPENDED", "已挂起"),
    FAILED("failed", 5, "FAILED", "已失败"),
    INTERRUPTED("interrupted", 6, "INTERRUPTED", "已中断"),
    CANCELLED("cancelled", 7, "CANCELLED", "已取消"),
    REJECTED("rejected", 8, "REJECTED", "已拒绝"),
    UNKNOWN("unknown", 9, "UNKNOWN", "未知");

    /** 原生状态值 */
    private final String nativeValue;
    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    NativeTaskState(String nativeValue, int code, String name, String desc) {
        this.nativeValue = nativeValue;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public String nativeValue() {
        return nativeValue;
    }

    public static NativeTaskState from(Object value) {
        for (NativeTaskState state : values()) {
            if (state.nativeValue.equals(value)) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
