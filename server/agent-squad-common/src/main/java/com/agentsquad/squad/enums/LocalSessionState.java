package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 本进程执行的取消观察状态
 * <p>
 * 只管理本机 SDK 资源，不替代持久任务与回复的业务状态。
 */
@Getter
public enum LocalSessionState {
    ACTIVE(1, "ACTIVE", "运行中"),
    STOP_REQUESTED(2, "STOP_REQUESTED", "已请求停止"),
    FINISHED(3, "FINISHED", "已结束");

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

    LocalSessionState(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
