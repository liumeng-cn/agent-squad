package com.agentsquad.squad.enums;

/**
 * SquadMsg 事件分类，用于前端归类展示。
 */
public enum SquadEventCategory {
    /**
     * Turn 与 Agent 生命周期事件。
     */
    LIFECYCLE,
    /**
     * 模型调用事件。
     */
    MODEL,
    /**
     * 普通消息与文本内容块事件。
     */
    MESSAGE,
    /**
     * 思考摘要内容块事件。
     */
    THINKING,
    /**
     * 工具调用及执行结果事件。
     */
    TOOL,
    /**
     * SubAgent 创建与执行关系事件。
     */
    SUBAGENT,
    /**
     * 执行中断、恢复等控制事件。
     */
    CONTROL,
    /**
     * 人工确认事件。
     */
    CONFIRMATION,
    /**
     * 执行错误事件。
     */
    ERROR,
    /**
     * Squad 或业务扩展事件。
     */
    CUSTOM
}
