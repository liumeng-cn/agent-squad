package com.agentsquad.squad.user.server.bo;

import lombok.Data;


import com.agentsquad.squad.common.enums.RbacStatus;
import com.agentsquad.squad.common.enums.MenuType;

/**
 * 菜单查询投影
 * <p>
 * 用户管理模块菜单授权数据，区分目录结构与可执行功能。
 */
@Data
public class MenuBO {
    /**
     * 菜单业务标识
     */
    private String menuId;
    /**
     * 父菜单业务标识
     */
    private String parentMenuId;
    /**
     * 菜单编码
     */
    private String code;
    /**
     * 菜单名称
     */
    private String name;
    /**
     * 节点类型
     */
    private MenuType type;
    /**
     * 已注册路由名称
     */
    private String routeName;
    /**
     * 页面路径
     */
    private String routePath;
    /**
     * 图标名称
     */
    private String icon;
    /**
     * 排序
     */
    private Integer sortOrder;
    /**
     * 目录状态
     */
    private RbacStatus status;
}
