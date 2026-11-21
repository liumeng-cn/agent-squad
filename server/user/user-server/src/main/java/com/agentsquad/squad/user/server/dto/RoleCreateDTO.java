package com.agentsquad.squad.user.server.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 新增角色入参
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class RoleCreateDTO {
    /**
     * 唯一编码
     */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{1,63}")
    private String code;

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


}
