package com.agentsquad.squad.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 管理状态定义
 * <p>
 * 表达管理授权有限值，与普通账号登录状态分开维护。
 */
@Getter
@RequiredArgsConstructor
public enum RbacOperation {
    ROLE_CREATE("新增角色"),
    ROLE_EDIT("修改角色"),
    ROLE_APPLY("应用权限"),
    ROLE_STATUS("角色启停"),
    MEMBER_ROLES("用户授权"),
    MEMBER_STATUS("管理用户启停");

    private final String desc;
}
