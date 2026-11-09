package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 账号角色。
 */
@Getter
public enum AccountRole {
    USER(1, "USER", "普通用户"),
    ADMIN(2, "ADMIN", "管理员"),
    OPERATOR(3, "OPERATOR", "运营人员");

    /**
     * 编码
     */
    private final int code;
    /**
     * 英文名
     */
    private final String name;
    /**
     * 中文描述
     */
    private final String desc;

    AccountRole(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
