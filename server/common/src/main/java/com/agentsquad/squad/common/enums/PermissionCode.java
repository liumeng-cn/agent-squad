package com.agentsquad.squad.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 管理权限目录
 * <p>
 * 表达管理授权有限值，与普通账号登录状态分开维护。
 */
@Getter
@RequiredArgsConstructor
public enum PermissionCode {
    ACCESS("management:access", "进入管理平台"),
    USER_READ("management:user:read", "查看管理用户"),
    USER_MANAGE("management:user:manage", "管理用户授权"),
    ROLE_READ("management:role:read", "查看管理角色"),
    ROLE_MANAGE("management:role:manage", "维护角色权限"),
    PERMISSION_READ("management:permission:read", "查看权限目录");

    private final String code;

    private final String desc;
}
