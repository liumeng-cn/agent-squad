package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.McpTransport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * MCP 连接草稿
 * <p>
 * 新建或编辑 MCP 服务版本草稿；编辑已有草稿须携带修订号，凭据只提交引用名。
 */
@Data
public class McpServerDraftDTO {
    /**
     * 编辑已有草稿时的预期修订号
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 传输方式
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
     * 凭据引用名
     */
    @Size(max = 80)
    private String credentialRef;

    /**
     * 附加请求头，值可写凭据占位符
     */
    @NotNull
    @Valid
    @Size(max = 20)
    private List<@NotNull KeyValueDTO> headers = List.of();

    /**
     * 连接与调用超时毫秒数
     */
    @NotNull
    @Min(1000)
    @Max(120000)
    private Integer timeoutMs = 30000;
}
