package com.agentsquad.squad.v2.bo;

import lombok.Data;

import java.util.Map;

/**
 * MCP 远程工具定义
 * <p>
 * 从远程服务发现的工具名称、说明和参数结构。
 */
@Data
public class McpRemoteToolBO {
    /**
     * 远程工具名称
     */
    private String name;

    /**
     * 远程工具标题
     */
    private String title;

    /**
     * 用途说明
     */
    private String description;

    /**
     * 参数 JSON Schema
     */
    private Map<String, Object> inputSchema;

    /**
     * 远程服务声明的只读提示
     */
    private boolean readOnly;
}
