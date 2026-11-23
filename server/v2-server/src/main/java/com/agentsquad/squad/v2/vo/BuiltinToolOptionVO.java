package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.Map;

/**
 * 内置工具选项
 * <p>
 * 可配置的内置实现及其默认定义，选择后带入工具草稿。
 */
@Data
public class BuiltinToolOptionVO {
    /**
     * 实现标识
     */
    private String implementationKey;

    /**
     * 默认工具名称
     */
    private String exposedName;

    /**
     * 默认用途说明
     */
    private String description;

    /**
     * 参数 JSON Schema
     */
    private Map<String, Object> inputSchema;

    /**
     * 是否只读
     */
    private boolean readOnly;
}
