package com.agentsquad.squad.user.server.bo;

import lombok.Data;

/**
 * 管理权限查询投影
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class PermissionBO {
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
