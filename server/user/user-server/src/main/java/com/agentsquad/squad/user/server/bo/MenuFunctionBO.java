package com.agentsquad.squad.user.server.bo;

import lombok.Data;


import com.agentsquad.squad.common.enums.RbacStatus;

/**
 * 功能目录查询投影
 * <p>
 * 用户管理模块菜单授权数据，区分目录结构与可执行功能。
 */
@Data
public class MenuFunctionBO {
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
}
