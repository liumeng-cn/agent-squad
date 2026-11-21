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
public enum RbacStatus {
    ACTIVE("启用"),
    DISABLED("禁用");

    private final String desc;
}
