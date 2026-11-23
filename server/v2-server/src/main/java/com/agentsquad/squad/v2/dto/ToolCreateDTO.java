package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.ToolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工具建档请求
 * <p>
 * 同一事务创建工具目录与第一个版本草稿，类型创建后不可修改。
 */
@Data
public class ToolCreateDTO {
    /**
     * 工具编码，创建后不可修改
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String toolCode;

    /**
     * 展示名称
     */
    @NotBlank
    @Size(max = 120)
    private String toolName;

    /**
     * 工具类型
     */
    @NotNull
    private ToolType toolType;

    /**
     * 第一个版本草稿
     */
    @NotNull
    @Valid
    private ToolDraftDTO draft;
}
