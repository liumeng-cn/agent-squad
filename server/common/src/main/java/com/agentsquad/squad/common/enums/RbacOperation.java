package com.agentsquad.squad.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 授权操作类型
 * <p>
 * 标识授权变更日志的操作类别，仅用于操作留痕，不参与鉴权判断。
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
