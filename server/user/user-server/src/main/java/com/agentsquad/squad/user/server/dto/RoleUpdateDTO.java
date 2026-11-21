package com.agentsquad.squad.user.server.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import jakarta.validation.constraints.Min;

/**
 * 修改角色入参
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class RoleUpdateDTO {
    /**
     * 中文名称
     */
    @NotBlank
    @Size(max = 64)
    private String name;

    /**
     * 职责说明
     */
    @NotNull
    @Size(max = 500)
    private String description;

    /**
     * 草稿权限编码
     */
    @NotNull
    @Size(max = 100)
    private List<String> permissionCodes;

    /**
     * 编辑版本
     */
    @NotNull
    @Min(0)
    private Long version;
}
