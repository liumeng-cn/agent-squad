package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 持久执行工作的就绪通道
 * <p>
 * 命令、控制和原生投影独立消费，避免相互占用就绪窗口。
 */
@Getter
public enum RedisWakeupChannel {
    COMMAND(1, "COMMAND", "命令通道"),
    CONTROL(2, "CONTROL", "控制通道"),
    PROJECTION(3, "PROJECTION", "投影通道");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    RedisWakeupChannel(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
