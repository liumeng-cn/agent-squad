package com.agentsquad.squad.v2.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 工具来源
 * <p>
 * 对应管理配置和数据库有限取值。
 */
@Getter
@RequiredArgsConstructor
public enum ToolSource {
    NATIVE("SDK原生工具"),
    JAVA("平台Java工具"),
    MCP("远程MCP工具");

    /**
     * 中文描述
     */
    private final String desc;
}
