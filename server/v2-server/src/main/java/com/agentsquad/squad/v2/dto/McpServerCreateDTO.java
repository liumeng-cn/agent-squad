package com.agentsquad.squad.v2.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * MCP连接建档
 * <p>
 * 提交稳定编码及展示信息。
 */
@Data
public class McpServerCreateDTO {
    /**
     * 连接编码
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
}
