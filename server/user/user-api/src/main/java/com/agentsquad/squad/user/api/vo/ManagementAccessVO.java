package com.agentsquad.squad.user.api.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理访问权限
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class ManagementAccessVO {
    /**
     * 是否允许进入管理平台
     */
    private boolean canAccess;

    /**
     * 当前有效权限编码
     */
    private List<String> permissionCodes;
}
