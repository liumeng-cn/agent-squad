package com.agentsquad.squad.v2.enums;

/**
 * 聊天公开事件类型
 * <p>
 * 仅声明当前页面支持的事件，不随 SDK 自动增加取值。
 */
public enum ChatEventType {
    RUN_STARTED,
    RUN_FINISHED,
    RUN_ERROR,
    TEXT_MESSAGE_CONTENT,
    REASONING_MESSAGE_CONTENT,
    TOOL_CALL_START,
    TOOL_CALL_RESULT
}
