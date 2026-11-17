package com.agentsquad.squad.common.enums;


/**
 * 用户账号状态
 * <p>
 * 所属common模块，按明确职责维护用户身份与数据边界。
 */
@lombok.Getter
public enum UserStatus {
    ACTIVE("正常"),
    DISABLED("禁用");

    /**
     * 中文含义
     */
    private final String desc;

    UserStatus(String desc) {
        this.desc = desc;
    }
}
