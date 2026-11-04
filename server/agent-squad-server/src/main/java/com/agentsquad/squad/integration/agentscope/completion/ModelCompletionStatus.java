package com.agentsquad.squad.integration.agentscope.completion;

/**
 * 模型调用技术状态
 * <p>
 * 只表达已提交模型结束事件，不代表回复、任务或预算已经完成。
 */
public enum ModelCompletionStatus {
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN;

    static ModelCompletionStatus fromNative(Object status) {
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
