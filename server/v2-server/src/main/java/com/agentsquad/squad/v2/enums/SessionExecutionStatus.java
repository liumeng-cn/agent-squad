package com.agentsquad.squad.v2.enums;


/**
 * 会话展示状态
 * <p>
 * 会话接口状态取值，当前由mock数据提供
 */
@lombok.Getter
public enum SessionExecutionStatus {
    UNKNOWN("待核对"),
    IDLE("空闲"),
    QUEUED("排队中"),
    RUNNING("执行中"),
    COMPLETED("已完成"),
    FAILED("执行失败"),
    WAITING("等待交互"),
    INTERRUPTED("已中断");

    /**
     * 中文含义
     */
    private final String desc;

    SessionExecutionStatus(String desc) {
        this.desc = desc;
    }
}
