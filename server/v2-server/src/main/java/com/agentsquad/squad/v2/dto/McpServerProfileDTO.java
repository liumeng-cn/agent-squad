package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MCP 服务资料修改请求
 * <p>
 * 只修改名称与说明，编码和版本内容不在此调整。
 */
@Data
public class McpServerProfileDTO {
    /**
     * 展示名称
     */
    @NotBlank
    @Size(max = 120)
    private String serverName;

    /**
     * 服务用途说明
     */
    @NotNull
    @Size(max = 500)
    private String description = "";

    /**
     * 目录预期修订号
     */
    @NotNull
    @PositiveOrZero
    private Long revision;
}
