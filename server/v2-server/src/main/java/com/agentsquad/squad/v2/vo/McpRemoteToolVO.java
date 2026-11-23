package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.Map;

/**
 * MCP 远程工具视图
 * <p>
 * 测试连接或选择远程工具时展示的工具定义。
 */
@Data
public class McpRemoteToolVO {
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
