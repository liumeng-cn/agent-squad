package com.agentsquad.squad.v2.bo;

import lombok.Data;

import java.util.Map;

/**
 * 内置工具实现
 * <p>
 * 代码登记的可配置工具实现及其默认的名称、说明、参数和只读标记。
 */
@Data
public class BuiltinToolBO {
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
