package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生聊天状态拒绝说明
 * <p>
 * 只翻译已知业务准入信息，未知基础设施错误保持服务故障语义。
 */
@Getter
public enum NativeChatStateRejection {
    MODEL_DISABLED("聊天模型尚未启用", "聊天模型尚未启用"),
    TURN_MISSING("目标任务轮次不存在或尚未完成", "目标轮次不存在或尚未完成"),
    TURN_CHANGED("Execution is no longer active", "目标执行已结束或发生变化"),
    UNFINISHED("Continue the unfinished task before starting another task", "请先继续未完成的任务"),
    PENDING("Answer pending interactions first", "请先处理待确认事项"),
    LATEST_ONLY("Only the latest unfinished task can continue", "只能继续最近的未完成轮次"),
    UNCERTAIN("Reconcile uncertain tool outcomes first", "工具结果尚未确认，暂不能继续"),
    ACTIVE("Wait for the current execution to stop", "请等待当前执行停止"),
    ALREADY_QUEUED("Continuation already queued", "继续请求已在等待执行"),
    UNKNOWN("", "当前操作结果尚未确认，请核对执行状态");
    /**
     * SDK 或应用边界信息。
     */
    private final String message;
    /**
     * 中文说明。
     */
    private final String desc;

    NativeChatStateRejection(String message, String desc) {
        this.message = message;
        this.desc = desc;
    }

    public static NativeChatStateRejection fromMessage(String message) {
        for (var value : values()) {
            if (value != UNKNOWN && value.message.equals(message)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
