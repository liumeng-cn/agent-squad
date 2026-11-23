package com.agentsquad.squad.v2.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 工具草稿请求
 * <p>
 * 按工具类型只填写对应的实现字段；内置工具的参数与只读标记以代码实现为准。
 */
@Data
public class ToolDraftDTO {
    /**
     * 编辑已有草稿时的预期修订号
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 暴露给模型的工具名称
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}")
    private String exposedName;

    /**
     * 模型可见的用途说明，发布前必填
     */
    @NotNull
    @Size(max = 2000)
    private String description = "";

    /**
     * 调用参数的 JSON Schema
     */
    @NotNull
    private Map<String, Object> inputSchema;

    /**
     * 是否只读取信息、不产生外部副作用
     */
    private boolean readOnly = true;

    /**
     * 内置工具实现标识
     */
    @Size(max = 160)
    private String implementationKey;

    /**
     * MCP 工具使用的服务版本ID
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String mcpServerVersionId;

    /**
     * MCP 服务中的工具名称
     */
    @Size(max = 255)
    private String remoteToolName;

    /**
     * HTTP 工具的请求模板
     */
    @Valid
    private HttpToolSpecDTO httpSpec;
}
