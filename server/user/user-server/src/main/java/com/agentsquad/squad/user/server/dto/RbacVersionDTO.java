package com.agentsquad.squad.user.server.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

/**
 * 管理版本校验
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class RbacVersionDTO {
    /**
     * 当前编辑版本
     */
    @NotNull
    @Min(0)
    private Long version;
}
