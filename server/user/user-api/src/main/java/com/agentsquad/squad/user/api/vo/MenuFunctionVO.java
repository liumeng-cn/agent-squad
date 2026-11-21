package com.agentsquad.squad.user.api.vo;

import lombok.Data;


import com.agentsquad.squad.common.enums.RbacStatus;

/**
 * 功能授权节点
 * <p>
 * 用户管理模块菜单授权数据，区分目录结构与可执行功能。
 */
@Data
public class MenuFunctionVO {
    /**
     * 功能编码
     */
    private String code;
    /**
     * 功能名称
     */
    private String name;
    /**
     * 所属页面
     */
    private String menuId;
    /**
     * 功能状态
     */
    private RbacStatus status;
    /**
     * 排序
     */
    private Integer sortOrder;
    /**
     * 是否可分配
     */
    private boolean selectable;
    /**
     * 禁选原因
     */
    private String disabledReason;
}
