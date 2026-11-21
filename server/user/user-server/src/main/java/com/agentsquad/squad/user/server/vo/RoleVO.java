package com.agentsquad.squad.user.server.vo;

import lombok.Data;
import com.agentsquad.squad.common.enums.RbacStatus;

import java.time.Instant;
import java.util.List;

/**
 * 管理角色响应
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class RoleVO {
    /** 草稿菜单业务标识 */
    private List<String> draftMenuIds;

    /** 已应用菜单业务标识 */
    private List<String> appliedMenuIds;

    /**
     * 角色业务ID
     */
    private String roleId;

    /**
     * 角色编码
     */
    private String code;

    /**
     * 中文名称
     */
    private String name;

    /**
     * 职责说明
     */
    private String description;

    /**
     * 启用状态
     */
    private RbacStatus status;

    /**
     * 是否内置
     */
    private Boolean builtIn;

    /**
     * 编辑版本
     */
    private Long version;

    /**
     * 应用版本
     */
    private Long appliedVersion;

    /**
     * 关联用户数
     */
    private long memberCount;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;

    /**
     * 草稿权限编码
     */
    private List<String> draftPermissionCodes;

    /**
     * 已应用权限编码
     */
    private List<String> appliedPermissionCodes;
}
