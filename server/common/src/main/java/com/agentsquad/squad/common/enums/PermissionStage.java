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
public enum PermissionStage {
    DRAFT("草稿权限"),
    APPLIED("已应用权限");

    private final String desc;
}
