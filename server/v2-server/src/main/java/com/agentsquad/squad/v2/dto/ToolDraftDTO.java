package com.agentsquad.squad.v2.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 工具草稿
 * <p>
 * 编辑现有草稿须携带修订号。
 */
@Data
public class ToolDraftDTO {
    /**
     * 预期修订号
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 模型工具名称
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}")
    private String exposedName;

    /**
     * 原生或Java实现版本标识
     */
    @Size(max = 160)
    private String implementationKey;

    /**
     * MCP连接版本ID
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String mcpServerVersionId;

    /**
     * 远端工具名称
     */
    @Size(max = 255)
    private String remoteToolName;
}
