package com.agentsquad.squad.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前用户鉴权请求
 * <p>
 * 用于受网络隔离保护的内部接口，用户编号由网关认证会话提供。
 */
@Data
public class PermissionCheckDTO {
    /**
     * 网关认证会话中的用户编号
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,17}")
    private String userId;

    /**
     * 目标请求方法
     */
    @NotBlank
    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
    private String method;

    /**
     * 包含工程前缀的目标请求路径
     */
    @NotBlank
    @Size(max = 2048)
    private String path;
}
