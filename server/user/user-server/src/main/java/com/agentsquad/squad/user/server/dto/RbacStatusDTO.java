package com.agentsquad.squad.user.server.dto;

import lombok.Data;
import com.agentsquad.squad.common.enums.RbacStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

/**
 * 管理启停入参
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class RbacStatusDTO {
    /**
     * 目标状态
     */
    @NotNull
    private RbacStatus status;

    /**
     * 编辑版本
     */
    @NotNull
    @Min(0)
    private Long version;
}
