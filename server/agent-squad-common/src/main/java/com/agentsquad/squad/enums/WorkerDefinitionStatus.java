package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * Worker 能力版本状态。
 * <p>
 * REVOKED 为终态，调度器只能选择 ENABLED 版本派发新任务。
 */
@Getter
public enum WorkerDefinitionStatus {
    ENABLED(1, "ENABLED", "已启用"),
    DISABLED(2, "DISABLED", "已停用"),
    REVOKED(3, "REVOKED", "已撤销");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    WorkerDefinitionStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public boolean canChangeTo(WorkerDefinitionStatus next) {
        return this != REVOKED && next != null;
    }
}
