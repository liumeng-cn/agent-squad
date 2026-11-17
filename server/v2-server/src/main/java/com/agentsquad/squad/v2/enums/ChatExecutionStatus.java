package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 聊天执行状态
 * <p>
 * 声明聊天模块公开状态及中文说明，与接口和前端取值保持一致。
 */
@Getter
public enum ChatExecutionStatus {
    IDLE("空闲"),
    QUEUED("等待执行"),
    RUNNING("执行中"),
    COMPLETED("已完成"),
    FAILED("执行失败"),
    WAITING("等待交互"),
    INTERRUPTED("已中断");

    /**
     * 中文含义。
     */
    private final String desc;

    ChatExecutionStatus(String desc) {
        this.desc = desc;
    }
}
