package com.agentsquad.squad.user.server.dto;

import lombok.Data;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

/**
 * 用户授权入参
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class UserRolesDTO {
    /**
     * 替换后的角色业务ID
     */
    @NotNull
    @Size(max = 50)
    private List<String> roleIds;

    /**
     * 管理授权版本
     */
    @NotNull
    @Min(0)
    private Long version;
}
