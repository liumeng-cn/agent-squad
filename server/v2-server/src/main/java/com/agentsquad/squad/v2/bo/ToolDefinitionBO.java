package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ToolType;
import lombok.Data;

import java.util.Map;

/**
 * 可执行工具定义
 * <p>
 * 由工具版本配置整理出的生成参数；凭据与 MCP 连接已解析，只在服务端内存中使用。
 */
@Data
public class ToolDefinitionBO {
    /**
     * 工具类型
     */
    private ToolType toolType;

    /**
     * 暴露给模型的工具名称
     */
    private String exposedName;

    /**
     * 模型可见的用途说明
     */
    private String description;

    /**
     * 调用参数的 JSON Schema
     */
    private Map<String, Object> inputSchema;

    /**
     * 是否只读
     */
    private boolean readOnly;

    /**
     * 内置工具实现标识
     */
    private String implementationKey;

    /**
     * HTTP 请求模板
     */
    private HttpToolSpecBO httpSpec;

    /**
     * HTTP 工具使用的凭据值
     */
    private String credential;

    /**
     * MCP 工具使用的连接参数
     */
    private McpConnectionBO mcpConnection;

    /**
     * MCP 服务展示名称与版本
     */
    private String mcpServerLabel;

    /**
     * MCP 服务中的工具名称
     */
    private String remoteToolName;
}
