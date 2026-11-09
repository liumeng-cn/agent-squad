package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 用户对话的生命周期状态。
 * <p>
 * ACTIVE 允许提交目标；DELETING 拦截新访问并等待在途目标收尾；DELETED 表示删除完成。
 */
@Getter
public enum ConversationStatus {
    ACTIVE(1, "ACTIVE", "活跃"),
    DELETING(2, "DELETING", "删除中"),
    DELETED(3, "DELETED", "已删除");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ConversationStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
