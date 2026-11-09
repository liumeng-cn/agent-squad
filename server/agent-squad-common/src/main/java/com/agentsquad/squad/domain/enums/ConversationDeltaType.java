package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 普通聊天直接应用的事件类型
 * <p>
 * 版本二载荷携带实体完整投影，旧载荷通过明确恢复处理，不冒充可直接应用的增量。
 */
@Getter
public enum ConversationDeltaType {
    MESSAGE_ACCEPTED("message.accepted", "MESSAGE_ACCEPTED", "消息已受理"),
    EXECUTION_QUEUED("execution.queued", "EXECUTION_QUEUED", "执行已排队"),
    EXECUTION_STATUS_CHANGED("execution.status_changed", "EXECUTION_STATUS_CHANGED", "执行状态变更"),
    MESSAGE_COMPLETED("message.completed", "MESSAGE_COMPLETED", "消息已完成"),
    PLAN_PUBLISHED("plan.published", "PLAN_PUBLISHED", "计划已发布"),
    JOB_REUSED("job.reused", "JOB_REUSED", "工作已复用");

    public static final int SCHEMA_VERSION = 2;
    /** 事件编码 */
    private final String code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ConversationDeltaType(String code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public String code() {
        return code;
    }
}
