package com.agentsquad.squad.v2.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MCP 服务建档请求
 * <p>
 * 同一事务创建服务目录与第一个版本草稿。
 */
@Data
public class McpServerCreateDTO {
    /**
     * 服务编码，创建后不可修改
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String serverCode;

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
     * 第一个版本草稿
     */
    @NotNull
    @Valid
    private McpServerDraftDTO draft;
}
