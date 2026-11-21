package com.agentsquad.squad.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 菜单节点类型
 * <p>
 * 目录组织页面，页面关联功能权限，不保存任意可执行组件。
 */
@Getter
@RequiredArgsConstructor
public enum MenuType {
    DIRECTORY("目录"), PAGE("页面");
    private final String desc;
}
