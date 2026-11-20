package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 原生聊天协议取值
 * <p>
 * 集中转换固定 SDK 的协议字符串，未知取值不进入公开业务分支。
 */
@Getter
public enum NativeChatEventType {
    RUN_START("run/start", "执行开始"),
    RUN_END("run/end", "执行结束"),
    INPUT_STARTED("inbox/started", "输入命令已开始"),
    INPUT_APPLIED("inbox/applied", "输入已应用"),
    USER_MESSAGE("message/user", "用户消息"),
    MODEL_PREPARED("request/prepared", "模型请求已准备"),
    MODEL_CHUNK("model/chunk", "模型增量"),
    TURN_OUTPUT("turn/output", "轮次输出"),
    TOOL_REQUESTED("tool/requested", "工具请求"),
    ACTION_END("action/end", "工具动作结束"),
    INTERACTION_REQUESTED("interaction/requested", "用户交互已请求"),
    INTERACTION_RESOLVED("interaction/resolved", "用户交互已回答"),
    TURN_CANCELLED("turn/cancelled", "轮次已取消"),
    UNKNOWN("", "未公开事件");
    /**
     * SDK 协议值。
     */
    private final String value;
    /**
     * 中文含义。
     */
    private final String desc;

    NativeChatEventType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static NativeChatEventType fromValue(Object value) {
        for (var item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return UNKNOWN;
    }
}
