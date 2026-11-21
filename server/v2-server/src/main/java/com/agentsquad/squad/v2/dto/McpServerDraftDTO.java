package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * MCP连接草稿
 * <p>
 * 编辑现有草稿须携带修订号。
 */
@Data
public class McpServerDraftDTO {
    /**
     * 预期修订号
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 连接传输方式
     */
    @NotNull
    private McpTransport transportType;

    /**
     * 远程连接地址
     */
    @NotBlank
    @Size(max = 2048)
    private String endpoint;

    /**
     * 连接凭据引用
     */
    @Size(max = 255)
    private String credentialRef;
}
