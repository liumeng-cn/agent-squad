package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.ToolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 工具保存请求
 * <p>
 * 新增与编辑统一提交，编码由系统自动生成；保存时不阻断未填完的实现参数，但若选择了 MCP 服务，该 MCP 必须启用且就绪。
 */
@Data
public class ToolSaveDTO {
    /**
     * 编辑已有工具时的预期目录修订号，新增时为空
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 后台展示名称
     */
    @NotBlank
    @Size(max = 120)
    private String toolName;

    /**
     * 聊天前端事件展示别称，留空回退工具名称
     */
    @Size(max = 60)
    private String displayAlias = "";

    /**
     * 工具类型，仅新增时生效，创建后不可修改
     */
    private ToolType toolType;

    /**
     * 暴露给模型的工具名称
     */
    @Pattern(regexp = "^$|^[a-zA-Z0-9_-]{1,64}$")
    private String exposedName = "";

    /**
     * 模型可见的用途说明
     */
    @Size(max = 2000)
    private String description = "";

    /**
     * 调用参数的 JSON Schema
     */
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
     * MCP 工具关联的服务业务ID
     */
    @Pattern(regexp = "^$|^[1-9][0-9]{0,18}$")
    private String mcpServerId;

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
