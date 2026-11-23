package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 工具类型
 * <p>
 * 决定工具版本保存哪类实现配置，以及运行时如何生成可执行工具。
 */
@Getter
public enum ToolType {
    BUILTIN("内置工具，使用代码登记的实现"),
    HTTP("HTTP 接口工具，按请求模板调用接口"),
    MCP("MCP 工具，调用远程 MCP 服务提供的工具");

    private final String desc;

    ToolType(String desc) {
        this.desc = desc;
    }
}
