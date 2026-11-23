package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工具资料修改请求
 * <p>
 * 只修改展示名称，编码、类型和版本内容不在此调整。
 */
@Data
public class ToolProfileDTO {
    /**
     * 展示名称
     */
    @NotBlank
    @Size(max = 120)
    private String toolName;

    /**
     * 目录预期修订号
     */
    @NotNull
    @PositiveOrZero
    private Long revision;
}
