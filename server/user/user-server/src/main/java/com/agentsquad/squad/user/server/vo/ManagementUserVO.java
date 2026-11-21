package com.agentsquad.squad.user.server.vo;

import lombok.Data;
import com.agentsquad.squad.common.enums.ManagementUserStatus;

import java.util.List;
import java.time.Instant;

/**
 * 管理用户响应
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class ManagementUserVO {
    /**
     * 用户业务ID
     */
    private String userId;

    /**
     * 账号名称
     */
    private String username;

    /**
     * 用户昵称
     */
    private String displayName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 管理授权状态
     */
    private ManagementUserStatus status;

    /**
     * 管理授权版本
     */
    private Long version;

    /**
     * 管理角色名称摘要
     */
    private String roleNames;

    /**
     * 管理角色业务ID
     */
    private List<String> roleIds;

    /**
     * 授权更新时间
     */
    private Instant lastUpdateTime;
}
