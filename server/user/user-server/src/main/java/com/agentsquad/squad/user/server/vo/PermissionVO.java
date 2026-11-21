package com.agentsquad.squad.user.server.vo;

import lombok.Data;

/**
 * 管理权限响应
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class PermissionVO {
    /**
     * 权限编码
     */
    private String code;

    /**
     * 中文名称
     */
    private String name;

    /**
     * 所属模块
     */
    private String module;

    /**
     * 权限说明
     */
    private String description;
}
