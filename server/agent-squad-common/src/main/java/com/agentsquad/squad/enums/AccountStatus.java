package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 账号状态。
 */
@Getter
public enum AccountStatus {
    ACTIVE(1, "ACTIVE", "启用"),
    DISABLED(2, "DISABLED", "停用");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    AccountStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
