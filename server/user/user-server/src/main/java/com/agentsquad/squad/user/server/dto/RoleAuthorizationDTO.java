package com.agentsquad.squad.user.server.dto;

import lombok.Data;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

/**
 * 角色授权草稿
 * <p>
 * 完整替换菜单与功能草稿，版本冲突时不覆盖现有数据。
 */
@Data
public class RoleAuthorizationDTO {
    /**
     * 选中的菜单业务标识
     */
    @NotNull
    @Size(max = 100)
    private List<String> menuIds;
    /**
     * 选中的功能编码
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
