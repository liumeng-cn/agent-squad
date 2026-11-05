package com.agentsquad.squad.presentation.domain;

/**
 * 普通聊天直接应用的事件类型
 * <p>
 * 版本二载荷携带实体完整投影，旧载荷通过明确恢复处理，不冒充可直接应用的增量。
 */
public enum ConversationDeltaType {
    MESSAGE_ACCEPTED("message.accepted"),
    EXECUTION_QUEUED("execution.queued"),
    EXECUTION_STATUS_CHANGED("execution.status_changed"),
    MESSAGE_COMPLETED("message.completed");

    public static final int SCHEMA_VERSION = 2;
    private final String code;

    ConversationDeltaType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
