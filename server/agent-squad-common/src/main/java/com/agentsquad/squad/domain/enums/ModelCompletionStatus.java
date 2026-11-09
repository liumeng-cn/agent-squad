package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 模型调用技术状态
 * <p>
 * 只表达已提交模型结束事件，不代表回复、任务或预算已经完成。
 */
@Getter
public enum ModelCompletionStatus {
    COMPLETED(1, "COMPLETED", "已完成"),
    FAILED(2, "FAILED", "已失败"),
    CANCELLED(3, "CANCELLED", "已取消"),
    UNKNOWN(4, "UNKNOWN", "未知");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ModelCompletionStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public static ModelCompletionStatus fromNative(Object status) {
        if (!(status instanceof String value)) {
            return UNKNOWN;
        }
        return switch (value) {
            case "completed" -> COMPLETED;
            case "failed" -> FAILED;
            case "cancelled" -> CANCELLED;
            default -> UNKNOWN;
        };
    }
}
