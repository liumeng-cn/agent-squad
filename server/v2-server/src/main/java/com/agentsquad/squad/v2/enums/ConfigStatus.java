package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 配置启停状态
 * <p>
 * MCP 服务、工具和 Agent 目录共用；停用后不能被新版本引用，存在生效引用时不能停用。
 */
@Getter
public enum ConfigStatus {
    ENABLED("启用"),
    DISABLED("停用");

    private final String desc;

    ConfigStatus(String desc) {
        this.desc = desc;
    }
}
