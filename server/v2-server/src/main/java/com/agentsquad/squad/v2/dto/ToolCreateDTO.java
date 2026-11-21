package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.ToolSource;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 工具建档
 * <p>
 * 提交稳定编码及展示信息。
 */
@Data
public class ToolCreateDTO {
    /**
     * 工具编码
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
     * 工具来源
     */
    @NotNull
    private ToolSource toolType;
}
