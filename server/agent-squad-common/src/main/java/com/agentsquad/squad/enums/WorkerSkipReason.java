package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 委派工作未执行的原因
 * <p>
 * 当前SKIPPED仅表示硬依赖已无法成功，取消和计划替换仍使用各自状态。
 */
@Getter
public enum WorkerSkipReason {
    DEPENDENCY_FAILED(1, "DEPENDENCY_FAILED", "依赖失败");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    WorkerSkipReason(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
