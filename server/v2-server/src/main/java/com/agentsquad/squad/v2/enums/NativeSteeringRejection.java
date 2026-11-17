package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生插话准入拒绝
 * <p>
 * 只识别固定 SDK 明确的轮次竞争拒绝；未知异常不允许降级为提交新任务。
 */
@Getter
public enum NativeSteeringRejection {
    NOT_RUNNING("No running task accepts steering; submit or resume explicitly", "目标任务不再运行"),
    STALE_TURN("Task is no longer running", "目标轮次已切换"),
    UNKNOWN("", "非明确准入拒绝");

    /**
     * 固定版本 SDK 拒绝信息。
     */
    private final String message;
    /**
     * 中文含义。
     */
    private final String desc;

    NativeSteeringRejection(String message, String desc) {
        this.message = message;
        this.desc = desc;
    }

    public static NativeSteeringRejection fromMessage(String message) {
        for (var item : values()) {
            if (item != UNKNOWN && item.message.equals(message)) {
                return item;
            }
        }
        return UNKNOWN;
    }
}
